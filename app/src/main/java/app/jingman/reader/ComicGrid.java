package app.jingman.reader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Three columns, twelve books per page, with portrait covers and local paging. */
public final class ComicGrid extends LinearLayout {
    public interface Actions {
        void open(SiteIndex.Entry book);
        void preview(SiteIndex.Entry book);
        void sourcePage(String url);
        void pageChanged(int index);
    }
    private static final int PAGE_SIZE=12;
    private final List<SiteIndex.Entry> books;
    private final CoverLoader covers;
    private final Actions actions;
    private final boolean dark;
    private final String previous,next;
    private final GridView grid;
    private final ReaderUi ui;
    private final ChapterCountLoader chapterCounts;
    private final boolean resolveMissingCovers;
    private final TextView counter;
    private final TextView back,forward;
    private int page,cardHeight;
    private final BaseAdapter adapter=new BaseAdapter(){
        public int getCount(){return Math.max(0,Math.min(PAGE_SIZE,books.size()-page*PAGE_SIZE));}
        public Object getItem(int position){return books.get(page*PAGE_SIZE+position);}
        public long getItemId(int position){return page*PAGE_SIZE+position;}
        public View getView(int position,View old,ViewGroup parent){
            Card holder;LinearLayout card;
            if(old==null){
                card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setPadding(0,0,0,0);
                FrameLayout picture=new FrameLayout(getContext());
                picture.setBackground(ui.shape(ui.soft,8));picture.setClipToOutline(true);
                ImageView image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.addView(image,new FrameLayout.LayoutParams(-1,-1));
                TextView state=ui.text("",9,ui.muted,false);state.setGravity(Gravity.CENTER);state.setSingleLine(true);state.setEllipsize(TextUtils.TruncateAt.END);
                picture.addView(state,new FrameLayout.LayoutParams(-1,dp(14),Gravity.BOTTOM));
                card.addView(picture,new LinearLayout.LayoutParams(-1,0,1));
                TextView title=ui.text("",12,ui.text,true);title.setGravity(Gravity.TOP|Gravity.LEFT);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(6),0,0);
                card.addView(title,new LinearLayout.LayoutParams(-1,dp(38)));
                TextView metadata=ui.text("",10,ui.muted,false);metadata.setMaxLines(2);metadata.setEllipsize(TextUtils.TruncateAt.END);metadata.setGravity(Gravity.TOP|Gravity.LEFT);
                card.addView(metadata,new LinearLayout.LayoutParams(-1,chapterCounts==null?0:dp(30)));
                metadata.setVisibility(chapterCounts==null?GONE:VISIBLE);
                holder=new Card(image,title,state,metadata);card.setTag(holder);
            }else{card=(LinearLayout)old;holder=(Card)card.getTag();}
            card.setLayoutParams(new AbsListView.LayoutParams(-1,cardHeight));
            SiteIndex.Entry entry=(SiteIndex.Entry)getItem(position);holder.title.setText(entry.title);card.setContentDescription(entry.title);
            if(chapterCounts!=null)chapterCounts.bind(holder.metadata,entry);
            if(resolveMissingCovers)covers.bind(holder.image,entry.cover,entry.url,holder.state);else covers.bindWithoutDetailLookup(holder.image,entry.cover,entry.url,holder.state);return card;
        }
    };
    private static final class Card {
        final ImageView image;final TextView title,state,metadata;
        Card(ImageView image,TextView title,TextView state,TextView metadata){this.image=image;this.title=title;this.state=state;this.metadata=metadata;}
    }
    public ComicGrid(Context context,List<SiteIndex.Entry> books,CoverLoader covers,boolean dark,String previous,String next,int initial,Actions actions){
        this(context,books,covers,dark,previous,next,initial,actions,null);
    }
    public ComicGrid(Context context,List<SiteIndex.Entry> books,CoverLoader covers,boolean dark,String previous,String next,int initial,Actions actions,ChapterCountLoader chapterCounts){
        this(context,books,covers,dark,previous,next,initial,actions,chapterCounts,true);
    }
    public ComicGrid(Context context,List<SiteIndex.Entry> books,CoverLoader covers,boolean dark,String previous,String next,int initial,Actions actions,ChapterCountLoader chapterCounts,boolean resolveMissingCovers){
        super(context);this.books=books;this.covers=covers;this.dark=dark;this.previous=previous;this.next=next;this.actions=actions;this.chapterCounts=chapterCounts;this.resolveMissingCovers=resolveMissingCovers;ui=new ReaderUi(context,dark);
        page=Math.max(0,Math.min(initial,Math.max(0,(books.size()-1)/PAGE_SIZE)));cardHeight=dp(140);setOrientation(VERTICAL);
        grid=new GridView(context);grid.setNumColumns(3);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(10));grid.setPadding(dp(20),dp(4),dp(20),dp(4));grid.setClipToPadding(false);grid.setSelector(android.R.color.transparent);grid.setAdapter(adapter);
        grid.setOnItemClickListener((a,v,p,id)->actions.open((SiteIndex.Entry)adapter.getItem(p)));
        grid.setOnItemLongClickListener((a,v,p,id)->{actions.preview((SiteIndex.Entry)adapter.getItem(p));return true;});
        addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pager=new LinearLayout(context);pager.setGravity(Gravity.CENTER_VERTICAL);pager.setPadding(dp(20),dp(4),dp(20),dp(7));
        back=pageButton("‹  上一页");forward=pageButton("下一页  ›");counter=ui.text("",11,ui.muted,false);counter.setGravity(Gravity.CENTER);
        pager.addView(back,new LinearLayout.LayoutParams(dp(86),dp(30)));pager.addView(counter,new LinearLayout.LayoutParams(0,dp(30),1));pager.addView(forward,new LinearLayout.LayoutParams(dp(86),dp(30)));addView(pager);
        if(books.size()<=PAGE_SIZE&&previous==null&&next==null)pager.setVisibility(GONE);
        back.setOnClickListener(v->{if(page>0){page--;update();}else if(previous!=null)actions.sourcePage(previous);});
        forward.setOnClickListener(v->{if((page+1)*PAGE_SIZE<books.size()){page++;update();}else if(next!=null)actions.sourcePage(next);});
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int column=(r-l-grid.getPaddingLeft()-grid.getPaddingRight()-2*dp(12))/3;int h=Math.max(dp(140),column*4/3+dp(38)+(chapterCounts==null?0:dp(30)));if(h!=cardHeight&&column>0){cardHeight=h;adapter.notifyDataSetChanged();}});
        update();
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    public boolean canScrollUp(){return grid.canScrollVertically(-1);}
    private TextView pageButton(String title){TextView t=ui.text(title,11,ui.muted,false);t.setGravity(Gravity.CENTER);t.setBackground(ui.touch(ui.soft,15));return t;}
    private void update(){if(chapterCounts!=null)chapterCounts.cancelRequests();counter.setText((page+1)+" / "+Math.max(1,(books.size()+PAGE_SIZE-1)/PAGE_SIZE));back.setEnabled(page>0||previous!=null);forward.setEnabled((page+1)*PAGE_SIZE<books.size()||next!=null);back.setAlpha(back.isEnabled()?1f:0.3f);forward.setAlpha(forward.isEnabled()?1f:0.3f);adapter.notifyDataSetChanged();grid.setSelection(0);actions.pageChanged(page);}
}
