package app.jingman.reader;
public class ImageCacheTest {
    static void check(boolean condition) { if(!condition) throw new AssertionError(); }
    static ImageLoader.Result mb(int n) { return new ImageLoader.Result(new byte[n*1024*1024],"image/webp"); }
    public static void main(String[] args) {
        check(ImageLoader.missing(new ImageLoader.HttpError(404)));
        check(ImageLoader.missing(new ImageLoader.HttpError(410)));
        check(!ImageLoader.missing(new ImageLoader.HttpError(403)));
        check(!ImageLoader.missing(new ImageLoader.HttpError(429)));
        check(!ImageLoader.missing(new java.io.IOException("timeout")));
        ImageLoader.clearCache();
        ImageLoader.remember("a",mb(5)); ImageLoader.remember("b",mb(5));
        check(ImageLoader.cached("a")!=null);
        ImageLoader.remember("c",mb(5));
        check(ImageLoader.cached("b")==null); check(ImageLoader.cached("a")!=null);
        check(ImageLoader.cachedBytes()<=ImageLoader.MAX_CACHE_BYTES);
        ImageLoader.remember("huge",mb(13)); check(ImageLoader.cached("huge")==null);
        ImageLoader.remember("a",mb(1)); check(ImageLoader.cachedBytes()==6*1024*1024);
        ImageLoader.clearCache(); check(ImageLoader.cachedBytes()==0); check(ImageLoader.cached("c")==null);
        System.out.println("Memory cache tests passed: 12 MiB cap, LRU eviction, replacement, clear.");
    }
}
