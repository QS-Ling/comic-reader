package app.jingman.reader;

import java.io.IOException;
import java.util.*;

/** Merge identical chapter labels, retaining every URL as an alternate and progress alias. */
public final class QimanChapters {
    public final LinkedHashMap<String,String> chapters=new LinkedHashMap<>();
    private final Map<String,String> aliases=new HashMap<>();
    private final Map<String,List<String>> alternatives=new HashMap<>();
    public String canonical(String path){return aliases.containsKey(path)?aliases.get(path):path;}
    public List<String> alternatives(String path){
        List<String> result=alternatives.get(canonical(path));
        return result==null?Collections.singletonList(path):result;
    }
    public String nextAlternative(String path,Set<String> attempted){
        for(String candidate:alternatives(path))if(!attempted.contains(candidate))return candidate;
        return null;
    }
    public static QimanChapters parse(String html)throws IOException {
        LinkedHashMap<String,String> links=ChapterTotals.qimanLinks(html);
        LinkedHashMap<String,List<String>> groups=new LinkedHashMap<>();
        for(Map.Entry<String,String> link:links.entrySet()){
            String label=link.getValue().replaceAll("[\\s\\u00a0\\u3000]+"," ").trim();
            // Empty labels cannot establish that two distinct URLs are the same chapter.
            String key=label.isEmpty()?link.getKey():label;
            List<String> group=groups.get(key);
            if(group==null){group=new ArrayList<>();groups.put(key,group);}
            group.add(link.getKey());
        }
        QimanChapters result=new QimanChapters();
        for(List<String> group:groups.values()){
            // The reported source appends replacement entries after the obsolete URL.
            String primary=group.get(group.size()-1);
            result.chapters.put(primary,links.get(primary));
            ArrayList<String> choices=new ArrayList<>(group);Collections.reverse(choices);
            result.alternatives.put(primary,Collections.unmodifiableList(choices));
            for(String path:group)result.aliases.put(path,primary);
        }
        return result;
    }
}
