package app.jingman.reader;

/** Map display positions without modifying canonical reading order or saved paths. */
public final class ChapterOrder {
    public static int index(int position,int count,boolean descending){
        if(position<0||position>=count)return -1;
        return descending?count-1-position:position;
    }
}
