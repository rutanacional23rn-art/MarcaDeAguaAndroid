package com.rutanacional23rn.marcadeagua;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_PHOTOS=10, PICK_LOGO=11;
    private FrameLayout preview; private ImageView photoView, logoView;
    private TextView status; private SeekBar opacityBar, sizeBar;
    private Uri logoUri; private Bitmap logoBitmap; private final ArrayList<Uri> photos=new ArrayList<>();
    private float logoX=.72f, logoY=.78f, downX, downY; private boolean ratioLocked=true;
    private String outputFormat="JPEG", outputSize="Original";
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("watermark",MODE_PRIVATE); buildUi(); loadPrefs(); }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(35,45,55)); return t; }
    private Button button(String s,Runnable r){ Button b=new Button(this); b.setText(s); b.setOnClickListener(v->r.run()); return b; }
    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(12),dp(16),dp(8)); root.setBackgroundColor(Color.WHITE);
        TextView title=text("MARCA DE AGUA",22); title.setTypeface(null,1); root.addView(title);
        TextView subtitle=text("Tus fotos, tu logo, todo en tu dispositivo",13); root.addView(subtitle);
        LinearLayout actions=new LinearLayout(this); actions.addView(button("Elegir fotos",()->pickPhotos()),new LinearLayout.LayoutParams(0,dp(52),1)); actions.addView(button("Elegir logo",()->pickLogo()),new LinearLayout.LayoutParams(0,dp(52),1)); root.addView(actions);
        preview=new FrameLayout(this); preview.setBackgroundColor(Color.rgb(238,241,244)); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1); pp.setMargins(0,dp(10),0,dp(10)); root.addView(preview,pp);
        photoView=new ImageView(this); photoView.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.addView(photoView,new FrameLayout.LayoutParams(-1,-1));
        logoView=new ImageView(this); logoView.setScaleType(ImageView.ScaleType.FIT_CENTER); FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(110),dp(110)); lp.gravity=Gravity.TOP|Gravity.START; preview.addView(logoView,lp); enableDrag();
        ScrollView controlsScroll=new ScrollView(this); LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL);
        controls.addView(text("Opacidad de la marca",14)); opacityBar=new SeekBar(this); opacityBar.setMax(100); opacityBar.setProgress(75); controls.addView(opacityBar);
        controls.addView(text("Tamaño de la marca",14)); sizeBar=new SeekBar(this); sizeBar.setMax(180); sizeBar.setProgress(70); controls.addView(sizeBar);
        CheckBox lock=new CheckBox(this); lock.setText("Mantener proporción del logo (candado)"); lock.setChecked(true); lock.setOnCheckedChangeListener((v,c)->ratioLocked=c); controls.addView(lock);
        Spinner format=new Spinner(this); format.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"JPEG","PNG"})); format.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?>p){} public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){outputFormat=pos==0?"JPEG":"PNG";}}); controls.addView(text("Formato de archivo",14)); controls.addView(format);
        Spinner size=new Spinner(this); size.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Original","Facebook cuadrado 1:1","Facebook vertical 4:5","Facebook horizontal 16:9","Instagram cuadrado 1:1","Instagram vertical 4:5","Instagram horizontal 1.91:1","Stories / Reels 9:16"})); size.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?>p){} public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){outputSize=new String[]{"Original","1:1","4:5","16:9","1:1","4:5","1.91:1","9:16"}[pos];}}); controls.addView(text("Formato para redes sociales",14)); controls.addView(size);
        LinearLayout exportRow=new LinearLayout(this); exportRow.addView(button("Guardar foto",()->export(false)),new LinearLayout.LayoutParams(0,dp(54),1)); exportRow.addView(button("Exportar lote",()->export(true)),new LinearLayout.LayoutParams(0,dp(54),1)); controls.addView(exportRow);
        status=text("Elegí una foto y un logo para comenzar.",13); controls.addView(status); controlsScroll.addView(controls); root.addView(controlsScroll,new LinearLayout.LayoutParams(-1,dp(240)));
        setContentView(root);
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){logoView.setAlpha(p/100f);savePrefs();} public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}});
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){resizeLogo();savePrefs();} public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}});
    }
    private void enableDrag(){ logoView.setOnTouchListener((v,e)->{ FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)v.getLayoutParams(); if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getRawX()-p.leftMargin;downY=e.getRawY()-p.topMargin;return true;} if(e.getAction()==MotionEvent.ACTION_MOVE){p.leftMargin=Math.max(0,Math.min(preview.getWidth()-v.getWidth(),(int)(e.getRawX()-downX)));p.topMargin=Math.max(0,Math.min(preview.getHeight()-v.getHeight(),(int)(e.getRawY()-downY)));v.setLayoutParams(p);logoX=preview.getWidth()==0?logoX:(float)p.leftMargin/preview.getWidth();logoY=preview.getHeight()==0?logoY:(float)p.topMargin/preview.getHeight();savePrefs();return true;}return e.getAction()==MotionEvent.ACTION_UP;}); }
    private void resizeLogo(){int w=dp(50+sizeBar.getProgress()); FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)logoView.getLayoutParams();p.width=w;p.height=w;logoView.setLayoutParams(p);}
    private void pickPhotos(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_PHOTOS);}
    private void pickLogo(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_LOGO);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;try{if(req==PICK_LOGO){logoUri=data.getData();getContentResolver().takePersistableUriPermission(logoUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);logoBitmap=decode(logoUri);logoView.setImageBitmap(logoBitmap);logoView.setVisibility(View.VISIBLE);resizeLogo();}else if(req==PICK_PHOTOS){photos.clear();if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)photos.add(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)photos.add(data.getData());if(!photos.isEmpty()){photoView.setImageURI(photos.get(0));status.setText(photos.size()+" foto(s) seleccionada(s). Arrastrá el logo para ubicarlo.");}}}catch(Exception e){status.setText("No se pudo abrir el archivo: "+e.getMessage());}}
    private Bitmap decode(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u)){return BitmapFactory.decodeStream(in);}}
    private void export(boolean batch){if(photos.isEmpty()){status.setText("Primero elegí una foto.");return;}if(logoBitmap==null){status.setText("Primero elegí tu logo.");return;}new Thread(()->{int count=0;for(int n=0;n<photos.size();n++){if(!batch&&n>0)break;try{Bitmap src=decode(photos.get(n));Bitmap out=compose(src);saveBitmap(out,"MarcaAgua_"+System.currentTimeMillis()+"_"+n);src.recycle();out.recycle();count++;}catch(Exception ignored){}}final int done=count;runOnUiThread(()->status.setText("Listo: "+done+" archivo(s) guardado(s) en Imágenes/MarcaDeAgua. Los originales no se modificaron."));}).start();}
    private Bitmap compose(Bitmap src){float target=ratio(outputSize);int w=src.getWidth(),h=src.getHeight();if(target>0){float current=(float)w/h;if(current>target)w=(int)(h*target);else h=(int)(w/target);}Bitmap cropped=Bitmap.createBitmap(src,(src.getWidth()-w)/2,(src.getHeight()-h)/2,w,h);int outW=Math.min(3000,w),outH=Math.min(3000,h);float scale=Math.min((float)outW/w,(float)outH/h);outW=Math.max(1,(int)(w*scale));outH=Math.max(1,(int)(h*scale));Bitmap out=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);Paint p=new Paint(3);c.drawBitmap(cropped,null,new Rect(0,0,outW,outH),p);float size=(50+sizeBar.getProgress())/ (float)Math.max(1,preview.getWidth())*outW;float x=logoX*outW,y=logoY*outH;RectF dst=new RectF(Math.max(0,x),Math.max(0,y),Math.min(outW,x+size),Math.min(outH,y+size));p.setAlpha(opacityBar.getProgress()*255/100);c.drawBitmap(logoBitmap,null,dst,p);cropped.recycle();return out;}
    private float ratio(String s){switch(s){case "1:1":return 1f;case "4:5":return .8f;case "16:9":return 16f/9f;case "1.91:1":return 1.91f;case "9:16":return 9f/16f;default:return -1f;}}
    private void saveBitmap(Bitmap b,String name)throws Exception{ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name+(outputFormat.equals("PNG")?".png":".jpg"));v.put(MediaStore.Images.Media.MIME_TYPE,outputFormat.equals("PNG")?"image/png":"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MarcaDeAgua");Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);try(OutputStream os=getContentResolver().openOutputStream(u)){b.compress(outputFormat.equals("PNG")?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,95,os);}}
    private void loadPrefs(){opacityBar.setProgress(prefs.getInt("opacity",75));sizeBar.setProgress(prefs.getInt("size",70));logoX=prefs.getFloat("x",.72f);logoY=prefs.getFloat("y",.78f);}
    private void savePrefs(){if(prefs!=null)prefs.edit().putInt("opacity",opacityBar.getProgress()).putInt("size",sizeBar.getProgress()).putFloat("x",logoX).putFloat("y",logoY).apply();}
}
