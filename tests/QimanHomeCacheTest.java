package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class QimanHomeCacheTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        SiteIndex.Page seed=QimanHomeCache.read(new FileInputStream("app/src/main/assets/qiman-home.tsv"));
        check(seed.savedHome&&seed.snapshotTime>0&&seed.books.size()>=20,"shipped real homepage available without network");
        check(seed.next==null&&seed.previous==null,"no imaginary remote pagination on saved homepage");
        SiteIndex.Page fallback=QimanHomeCache.withNote(seed,"官网返回403，显示已保存首页");
        check(fallback.savedHome&&fallback.snapshotTime==seed.snapshotTime&&fallback.books.size()==seed.books.size(),"403 preserves books and actual saved time");
        fallback.books.clear();check(!seed.books.isEmpty(),"independent fallback page containers");
        SiteIndex.Page live=new SiteIndex.Page();live.books.add(new SiteIndex.Entry(SiteIndex.ORIGIN+"/comic/AAA","新书 A"));live.books.add(new SiteIndex.Entry(SiteIndex.ORIGIN+"/comic/BBB","新书 B"));
        live.books.add(new SiteIndex.Entry(SiteIndex.ORIGIN+"/comic/AAA","新书 A"));live.books.add(new SiteIndex.Entry("https://evil.test/comic/CCC","站外书"));
        ByteArrayOutputStream saved=new ByteArrayOutputStream();QimanHomeCache.write(live,1234567890L,saved);
        SiteIndex.Page reloaded=QimanHomeCache.read(new ByteArrayInputStream(saved.toByteArray()));
        check(reloaded.books.size()==2&&reloaded.snapshotTime==1234567890L,"persist, deduplicate and isolate source");
        check(reloaded.books.get(0).title.equals("新书 A")&&reloaded.books.get(1).title.equals("新书 B"),"official homepage ordering preserved");
        check(QimanHomeCache.sameBooks(reloaded,QimanHomeCache.withNote(reloaded,"cached")),"same content need not recreate the grid");
        check(!QimanHomeCache.sameBooks(seed,reloaded),"changed homepage detected");
        for(String broken:Arrays.asList("","#home-updated\tbad\n","#home-updated\t123\nhttps://evil.test/comic/A\tWrong\t\n")){
            boolean failed=false;try{QimanHomeCache.read(new ByteArrayInputStream(broken.getBytes("UTF-8")));}catch(IOException expected){failed=true;}check(failed,"corrupt or empty snapshot rejected");
        }
        saved.reset();boolean failed=false;try{QimanHomeCache.write(new SiteIndex.Page(),1,saved);}catch(IOException expected){failed=true;}check(failed&&saved.size()==0,"empty server result cannot replace existing snapshot");
        System.out.println("Homepage cache passed: "+seed.books.size()+" official entries available offline; saved time, 403 preservation, refresh, ordering, isolation, corruption and empty-response guards.");
    }
}
