import app.jingman.reader.ImageLoader;
import java.nio.file.*;

/** Exercise the production Android image transport on the JVM, saving bytes for visual inspection. */
public class VerifyImages {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("URL OUTPUT");
        ImageLoader.Result result=ImageLoader.load(args[0]);
        Files.write(Paths.get(args[1]),result.bytes);
        System.out.println(result.mime+" "+result.bytes.length+" bytes -> "+args[1]);
    }
}
