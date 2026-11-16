package app.jingman.reader;

import java.io.*;
import java.security.*;
import java.util.*;

/** Persistent compressed images grouped by comic + chapter, independent of WebView cache. */
public final class ChapterCache {
    private final File root;
    private int epoch;
    private long limitBytes;
    public synchronized void setLimit(long bytes) throws IOException {
        limitBytes=Math.max(0,bytes); trim(null);
    }
    private void trim(File protectedChapter) throws IOException {
        if(limitBytes==0)return;
        File[] dirs=root.listFiles();if(dirs==null)return;
        Arrays.sort(dirs,Comparator.comparingLong(File::lastModified));
        long total=0;for(File dir:dirs)total+=size(dir);
        for(File dir:dirs){
            if(total<=limitBytes)break;
            if(!dir.isDirectory()||!dir.getName().matches("[a-f0-9]{64}")||dir.equals(protectedChapter))continue;
            long bytes=size(dir);epoch++;delete(dir);total-=bytes;
        }
    }
    private long size(File file) {
        if(!file.isDirectory())return file.length();
        long bytes=0;File[] children=file.listFiles();if(children!=null)for(File child:children)bytes+=size(child);return bytes;
    }
    public ChapterCache(File root) throws IOException { this.root=root.getCanonicalFile(); if(!root.isDirectory()&&!root.mkdirs()) throw new IOException("Cannot create chapter cache"); }
    public synchronized int epoch() { return epoch; }
    static String hash(String text) throws IOException {
        try { StringBuilder s=new StringBuilder(); for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"))) s.append(String.format(Locale.ROOT,"%02x",b&255)); return s.toString(); }
        catch(NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    File folder(String chapter) throws IOException { return new File(root,hash(chapter)); }
    public synchronized ImageLoader.Result get(String chapter,String address) throws IOException {
        File file=new File(folder(chapter),hash(address)+".img");
        if(!file.isFile()) return null;
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))) {
            String mime=in.readUTF(); int size=in.readInt();
            if(!mime.startsWith("image/")||size<=0||size>16*1024*1024||size>file.length()) throw new IOException("Invalid cached image");
            byte[] bytes=new byte[size]; in.readFully(bytes); file.getParentFile().setLastModified(System.currentTimeMillis()); return new ImageLoader.Result(bytes,mime);
        } catch(IOException e) { file.delete(); return null; }
    }
    public synchronized void put(String chapter,String title,String address,ImageLoader.Result result,int expectedEpoch) throws IOException {
        if(epoch!=expectedEpoch) return;
        File dir=folder(chapter); if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("Cannot save chapter");
        File file=new File(dir,hash(address)+".img"),temp=new File(dir,hash(address)+".tmp");
        try {
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(temp))) { out.writeUTF(result.mime); out.writeInt(result.bytes.length); out.write(result.bytes); }
            if(!temp.renameTo(file)) { if(file.exists()) file.delete(); if(!temp.renameTo(file)) throw new IOException("Cannot save image"); }
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(dir,"title")))) { out.writeUTF(title); }
            dir.setLastModified(System.currentTimeMillis());trim(dir);
        } finally { temp.delete(); }
    }
    public static final class Entry {
        public final String id,title; public final int pages; public final long bytes;
        Entry(String id,String title,int pages,long bytes) { this.id=id;this.title=title;this.pages=pages;this.bytes=bytes; }
        @Override public String toString() { return title+" · "+pages+" 张 · "+String.format(Locale.ROOT,"%.1f MB",bytes/1048576.0); }
    }
    public synchronized List<Entry> list() {
        List<Entry> result=new ArrayList<>(); File[] dirs=root.listFiles(); if(dirs==null) return result;
        for(File dir:dirs) {
            if(!dir.isDirectory()||!dir.getName().matches("[a-f0-9]{64}")) continue;
            String title="章节缓存"; try(DataInputStream in=new DataInputStream(new FileInputStream(new File(dir,"title")))) { title=in.readUTF(); } catch(IOException ignored) {}
            int pages=0;long bytes=0;File[] files=dir.listFiles();if(files!=null) for(File f:files) if(f.getName().endsWith(".img")){pages++;bytes+=f.length();}
            if(pages>0)result.add(new Entry(dir.getName(),title,pages,bytes));
        }
        result.sort(Comparator.comparing(e->e.title)); return result;
    }
    public synchronized void remove(String id) throws IOException {
        if(!id.matches("[a-f0-9]{64}")) throw new IOException("Invalid chapter id"); epoch++; delete(new File(root,id));
    }
    public synchronized void clear() throws IOException { epoch++;File[] files=root.listFiles();if(files!=null)for(File file:files)delete(file); }
    private void delete(File file) throws IOException {
        if(!file.getCanonicalPath().startsWith(root.getPath()+File.separator)) throw new IOException("Invalid cache path");
        if(file.isDirectory()){File[] files=file.listFiles();if(files!=null)for(File child:files)delete(child);}
        if(file.exists()&&!file.delete()) throw new IOException("Cannot remove cache file");
    }
}
