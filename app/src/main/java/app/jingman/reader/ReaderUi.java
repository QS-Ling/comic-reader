package app.jingman.reader;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Small native design system shared by discovery, library, detail and settings. */
public final class ReaderUi {
    public final Context context;public final boolean dark;
    public final int background,surface,soft,text,muted,accent,line,tint;
    public ReaderUi(Context context,boolean dark){
        this.context=context;this.dark=dark;
        background=Color.parseColor(dark?"#101116":"#FAF9FD");surface=Color.parseColor(dark?"#1A1B23":"#FFFFFF");
        soft=Color.parseColor(dark?"#23242E":"#F0EEF6");text=Color.parseColor(dark?"#F2F1F7":"#21202A");muted=Color.parseColor(dark?"#9696A8":"#8A869A");
        accent=Color.parseColor(dark?"#A597FF":"#7660DC");line=Color.parseColor(dark?"#2A2B35":"#EDEAF3");tint=Color.parseColor(dark?"#2C2742":"#EEE9FD");
    }
    public int dp(int value){return (int)(context.getResources().getDisplayMetrics().density*value+0.5f);}
    public TextView text(String value,int size,int color,boolean bold){
        TextView view=new TextView(context);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setFontFeatureSettings("kern");view.setIncludeFontPadding(false);view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;
    }
    public GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    public Drawable touch(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(dark?0x22FFFFFF:0x14000000),shape(color,radius),null);}
    public TextView chip(String title,boolean selected,Runnable action){
        TextView t=text(title,13,selected?accent:muted,selected);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setPadding(dp(14),0,dp(14),0);t.setBackground(touch(selected?tint:Color.TRANSPARENT,18));t.setOnClickListener(v->action.run());return t;
    }
    public TextView action(String title,boolean primary,Runnable action){
        TextView t=text(title,14,primary?(dark?background:Color.WHITE):accent,true);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding(dp(12),0,dp(12),0);t.setBackground(touch(primary?accent:tint,12));t.setOnClickListener(v->action.run());return t;
    }
    public View iconButton(String name,String label,Runnable action){
        FrameLayout box=new FrameLayout(context);box.setBackground(touch(Color.TRANSPARENT,24));Icon icon=new Icon(context,name,muted);box.addView(icon,new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));box.setContentDescription(label);box.setOnClickListener(v->action.run());return box;
    }
    public LinearLayout row(){LinearLayout l=new LinearLayout(context);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    public View divider(){View v=new View(context);v.setBackgroundColor(line);v.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(1,dp(1))));return v;}
    public LinearLayout bottomBar(String selected,Runnable discover,Runnable library,Runnable mine){
        LinearLayout wrapper=new LinearLayout(context);wrapper.setOrientation(LinearLayout.VERTICAL);wrapper.setBackgroundColor(surface);wrapper.addView(divider());
        LinearLayout row=row();String[] titles={"发现","书架","我的"},icons={"discover","book","user"};Runnable[] actions={discover,library,mine};
        for(int i=0;i<3;i++){
            boolean active=selected.equals(titles[i]);LinearLayout item=new LinearLayout(context);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setBackground(touch(Color.TRANSPARENT,0));
            FrameLayout iconBox=new FrameLayout(context);iconBox.setBackground(shape(active?tint:Color.TRANSPARENT,14));iconBox.addView(new Icon(context,icons[i],active?accent:muted),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));item.addView(iconBox,new LinearLayout.LayoutParams(dp(48),dp(29)));
            TextView label=text(titles[i],10,active?accent:muted,active);label.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(19));lp.topMargin=dp(2);item.addView(label,lp);
            final Runnable action=actions[i];item.setOnClickListener(v->action.run());item.setContentDescription(titles[i]);row.addView(item,new LinearLayout.LayoutParams(0,dp(60),1));
        }
        wrapper.addView(row);return wrapper;
    }
    public View menuRow(String icon,String title,String subtitle,Runnable action){
        LinearLayout row=row();row.setPadding(dp(16),dp(10),dp(12),dp(10));row.setBackground(touch(surface,14));
        FrameLayout badge=new FrameLayout(context);badge.setBackground(shape(tint,12));badge.addView(new Icon(context,icon,accent),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));row.addView(badge,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout copy=new LinearLayout(context);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(13),0,dp(8),0);copy.addView(text(title,14,text,true));if(!subtitle.isEmpty()){TextView small=text(subtitle,11,muted,false);small.setPadding(0,dp(5),0,0);copy.addView(small);}
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.addView(new Icon(context,"right",muted),new LinearLayout.LayoutParams(dp(17),dp(17)));row.setOnClickListener(v->action.run());return row;
    }
    public static final class Icon extends View {
        private final String name;private final Paint paint=new Paint(3);private final int color;
        public Icon(Context context,String name,int color){super(context);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void lines(Canvas c,float...p){for(int i=0;i+3<p.length;i+=4)c.drawLine(p[i],p[i+1],p[i+2],p[i+3],paint);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            switch(name){
                case "search":canvas.drawCircle(10.5f,10.5f,6.4f,paint);lines(canvas,15.4f,15.4f,21,21);break;
                case "back":lines(canvas,14.5f,5,7.5f,12,7.5f,12,14.5f,19);break;
                case "right":lines(canvas,9,6,15,12,15,12,9,18);break;
                case "down":lines(canvas,6,9,12,15,12,15,18,9);break;
                case "more":paint.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)canvas.drawCircle(5+i*7,12,1.6f,paint);break;
                case "refresh":canvas.drawArc(new RectF(4,4,20,20),-40,285,false,paint);lines(canvas,20,4,20,10,20,10,14,10);break;
                case "book":Path book=new Path();book.moveTo(3,4);book.lineTo(9,4);book.quadTo(12,4,12,6);book.quadTo(12,4,15,4);book.lineTo(21,4);book.lineTo(21,19);book.lineTo(15,19);book.quadTo(12,19,12,21);book.quadTo(12,19,9,19);book.lineTo(3,19);book.close();canvas.drawPath(book,paint);lines(canvas,12,6,12,18);break;
                case "discover":canvas.drawCircle(12,12,9,paint);Path needle=new Path();needle.moveTo(16,7);needle.lineTo(14,14);needle.lineTo(7,16);needle.lineTo(9,9);needle.close();canvas.drawPath(needle,paint);break;
                case "user":canvas.drawCircle(12,7.5f,3.4f,paint);canvas.drawArc(new RectF(4,13,20,25),185,170,false,paint);break;
                case "clock":canvas.drawCircle(12,12,9,paint);lines(canvas,12,6,12,12,12,12,16,14);break;
                case "moon":Path moon=new Path();moon.moveTo(20,15);moon.cubicTo(14,18,7,12,10,4);moon.cubicTo(-1,7,4,24,15,21);moon.quadTo(19,20,20,15);canvas.drawPath(moon,paint);break;
                case "link":canvas.drawRoundRect(new RectF(2,8,14,16),4,4,paint);canvas.drawRoundRect(new RectF(10,8,22,16),4,4,paint);break;
                case "source":canvas.drawRoundRect(new RectF(3,3,21,21),4,4,paint);lines(canvas,3,10,21,10,10,3,10,21);break;
                case "storage":canvas.drawRoundRect(new RectF(4,4,20,20),3,3,paint);lines(canvas,8,9,16,9,8,15,12,15);break;
                default:canvas.drawCircle(12,12,8,paint);lines(canvas,8,12,16,12);
            }canvas.restore();
        }
    }
}
