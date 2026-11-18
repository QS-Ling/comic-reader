package app.jingman.reader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HomeRecommendations {
    private static final int BOOKS_PER_SECTION = 3;

    static void prioritize(SiteIndex.Page page, List<List<SiteIndex.Entry>> sections) {
        Map<String, SiteIndex.Entry> remaining = new LinkedHashMap<>();
        for (SiteIndex.Entry book : page.books) remaining.put(book.url, book);

        page.books.clear();
        for (List<SiteIndex.Entry> section : sections) {
            int added = 0;
            for (SiteIndex.Entry candidate : section) {
                SiteIndex.Entry book = remaining.remove(candidate.url);
                if (book == null) continue;
                page.books.add(book);
                if (++added == BOOKS_PER_SECTION) break;
            }
        }
        page.books.addAll(remaining.values());
    }
}
