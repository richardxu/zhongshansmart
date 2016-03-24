package com.suntrans.zhongshui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import database.DbHelper;
import me.isming.sticker.library.StickerView;
import views.TouchListener;


/**
 * Created by 1111b on 2016/1/4.
 */
public class Edit_Activity extends Activity {


    private FrameLayout layout,layout1,layout_loading;
    private LinearLayout layout_back;
    private TextView tx_back,tx_edit,tx_save;
    private GridView gridview;
    private int result_code=0;
//    private ListView listview;
    private ArrayList<StickerView> stickerView=new ArrayList<>();     //存放标签的ArrayList数组
    private ImageView image;   //背景图片
    private String room;   //需要编辑的房间，是外间还是里间
    private ArrayList<Map<String, Object>> data = new ArrayList<>();  //存放数据，从通道1到通道10
    private Bitmap bg_bitmap;
    private ProgressDialog progressdialog;    //加载进度条
   // private String[] channels=new String[]{"通道1","通道2","通道3","通道4","通道5","通道6","通道7","通道8","通道9","通道10"};
    private Handler handler1=new Handler(){

        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            gridview.setAdapter(new Adapter());
            ViewInit1(true);
            layout_loading.setVisibility(View.GONE);
        }

    };
    private Handler handler2=new Handler()   //用来控制progressdialog的显示和销毁
    {
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            if(msg.what==0)   //如果是要关闭progresedialog的显示（保存成功后调用）
            {
                if(progressdialog!= null)
                {
                    progressdialog.dismiss();
                    progressdialog=null;
                    Toast.makeText(getApplicationContext(),"保存成功!",Toast.LENGTH_SHORT).show();
                }
                //which="100";
            }
            else if(msg.what==1)   //是要显示progressdialog
            {
                progressdialog = new ProgressDialog(Edit_Activity.this);    //初始化progressdialog
                progressdialog.setCancelable(true);// 设置是否可以通过点击Back键取消
                progressdialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
                progressdialog.show();
                progressdialog.setContentView(R.layout.progressdialog);    //设置显示的内容
            }
            else   //关闭progressdialog
            {
                if(progressdialog!= null)
                {
                    progressdialog.dismiss();
                    progressdialog=null;
                   // Toast.makeText(getApplicationContext(),"保存成功!",Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {   //事件分发
       // Log.i("Event", "layout事件分发");
        if(event.getAction()==MotionEvent.ACTION_DOWN)
            for (StickerView sv : stickerView)
                sv.setShowDrawController(false);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onResume(){
        super.onResume();
        layout_loading.setVisibility(View.VISIBLE);
        new Thread(){
            public void run(){
                DataInit();
                ViewInit();
            }
        }.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit);
        Intent intent=getIntent();
        room=intent.getStringExtra("Room");  //当前需要编辑的房间
        layout = (FrameLayout) findViewById(R.id.layout);   //显示标签的布局
        layout1 = (FrameLayout) findViewById(R.id.layout1);    //显示通道名称的布局
        layout_loading = (FrameLayout) findViewById(R.id.layout_loading);    //显示加载条的布局
        image = (ImageView) findViewById(R.id.image);
        tx_back = (TextView) findViewById(R.id.tx_back);
        tx_edit = (TextView) findViewById(R.id.tx_edit);
        tx_save = (TextView) findViewById(R.id.tx_save);
        layout_back = (LinearLayout) findViewById(R.id.layout_back);
        gridview = (GridView) findViewById(R.id.gridview);
        DbHelper dh1 = new DbHelper(Edit_Activity.this, "IBMS", null, 1);
        SQLiteDatabase db = dh1.getWritableDatabase();
        db.beginTransaction();
        Cursor cursor = db.query("room_tb", new String[]{"RID", "Name", "Image"}, "Name=?", new String[]{room}, null, null, null, null);
        while (cursor.moveToNext()) {
            byte[] in = cursor.getBlob(2);
            bg_bitmap = BitmapFactory.decodeByteArray(in, 0, in.length);
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        db.close();
        //bg_bitmap=BitmapFactory.decodeResource(getResources(),R.drawable.bg_room);
        image.setImageBitmap(bg_bitmap);   //设置背景图片
      //  tx_back.setText("编辑"+room);
        layout_back.setOnTouchListener(new TouchListener());   //返回按钮
        layout_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        tx_edit.setOnTouchListener(new TouchListener());   //更换背景图片
        tx_edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(Edit_Activity.this);
                builder.setTitle("更换背景图片：");
                builder.setItems(new String[]{"本地图库", "拍照", "取消"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //点击后弹出窗口选择了第几项
                        //Toast.makeText(getApplicationContext(),"你点击了第"+which+"个图标",Toast.LENGTH_SHORT).show();
                        switch (which) {
                            case 0:    //选择本地图库
                            {
                                result_code = 0;
                                //打开图库
                                Intent intent = new Intent(
                                        Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                startActivityForResult(intent, 10000); //请求码为10000，  用来区分图片是裁剪前的还是裁剪后的
                                break;
                            }
                            case 1:    //选择拍照
                            {
                                result_code = 0;
                                //拍照
                                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                                startActivityForResult(intent, 10001);  //请求码为10001， 用来区分图片是裁剪前的还是裁剪后的
                                break;
                            }
                            case 2://点击取消
                            {
                                break;
                            }
                            default:
                                break;
                        }
                    }
                });
                builder.create().show();
            }
        }) ;

        tx_save.setOnTouchListener(new TouchListener());   //保存
        tx_save.setOnClickListener(new View.OnClickListener() {   //先显示进度条，禁止操作，保存完成后关闭进度条的显示，并提示保存成功（Toast）
            @Override
            public void onClick(View v) {   //点击保存按钮进行的操作
                //  Toast.makeText(getApplicationContext(), "点击了保存按钮", Toast.LENGTH_SHORT).show();
                progressdialog = new ProgressDialog(Edit_Activity.this);    //初始化progressdialog
                progressdialog.setCancelable(true);// 设置是否可以通过点击Back键取消
                progressdialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
                progressdialog.show();
                progressdialog.setContentView(R.layout.progressdialog);    //设置显示的内容

                new Thread() {
                    public void run() {
                        DbHelper dh = new DbHelper(Edit_Activity.this, "IBMS", null, 1);
                        SQLiteDatabase db = dh.getWritableDatabase();
                        db.beginTransaction();
                        String str = "";
                        String Channel = "";
                        Bitmap bm;
                        //ByteArrayOutputStream os = new ByteArrayOutputStream();
                        for (int i = 0; i < data.size(); i++) {
                            Map<String, Object> map = data.get(i);
                            Channel = String.valueOf(i + 1);
                            String IsShow = map.get("IsShow").toString();
                            StickerView sv = (StickerView) map.get("StickerView");
                            ContentValues cv = new ContentValues();
                            cv.put("IsShow", IsShow);
                            if (sv != null) {
                                Matrix matrix = new Matrix(sv.getMarkMatrix());
                                float[] values = new float[9];
                                matrix.getValues(values);
                                str = "";
                                //bm=sv.getBitmap();
                                //bm.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
                                Channel = String.valueOf(sv.getTab());
                                for (float f : values) {
                                    str += String.valueOf(f);
                                    str += ";";
                                }    //共9个变量，每一个变量后面都加上分号“;”
                                cv.put("Matrix", str);
                            }
                            // cv.put("Image", os.toByteArray());
                            db.update("switchs_tb", cv, "Channel=? and Room=?", new String[]{Channel, room});
                        }
                        ContentValues cv = new ContentValues();
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        bg_bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
                        cv.put("Image", os.toByteArray());
                        db.update("room_tb", cv, "Name=?", new String[]{room});
                        db.setTransactionSuccessful();
                        db.endTransaction();
                        db.close();
                        Message msg = new Message();
                        msg.what = 0;
                        handler2.sendMessage(msg);   //关闭加载中对话框
                    }
                }.start();

//                progressdialog.dismiss();
//                progressdialog=null;
                //Toast.makeText(getApplicationContext(),"保存成功！",Toast.LENGTH_SHORT).show();
            }
        });

       // layout_loading.setVisibility(View.GONE);
    }

    /****
     * 数据初始化。提取数据库中两个房间开关通道的相关信息，分别存入data和data1中
     */
    private void DataInit()
    {
        data=new ArrayList<Map<String,Object>>();
        DbHelper dh1 = new DbHelper(Edit_Activity.this, "IBMS", null, 1);
        SQLiteDatabase db = dh1.getWritableDatabase();
        db.beginTransaction();
        Cursor cursor = db.query("switchs_tb", new String[]{"CID", "Name", "Image", "IsShow", "Matrix", "Channel"}, "Room=?", new String[]{room}, null, null, null, null);
        while (cursor.moveToNext()) {
            String IsShow = cursor.getString(3);   //是否需要显示，“1”代表需要显示，0代表不需要显示
            byte[] in = cursor.getBlob(2);     //获取图片
            String Name = cursor.getString(1);    //通道名称
            String Cid = cursor.getString(0);    //通道ID
            String matrix = cursor.getString(4);    //图片的参数矩阵
            String Channel = cursor.getString(5);   //通道号
            Bitmap bitmap = BitmapFactory.decodeByteArray(in, 0, in.length);   //转化成bitmap格式
            Map<String, Object> map=new HashMap<String,Object>();
            map.put("IsShow", IsShow);
            map.put("CID", Cid);
            map.put("Name", Name);
            map.put("Matrix", matrix);
            map.put("Image", bitmap);
            map.put("State", "false");
            map.put("Channel", Channel);
            data.add(map);
        }

        db.setTransactionSuccessful();
        db.endTransaction();
        db.close();
    }

    /*
     * 视图初始化，初始化背景图片，通道标签图片等
     */
    private void ViewInit() {
        stickerView = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            final Map<String, Object> map = data.get(i);  //获取第i+1通道的信息，更改map即可对data中的数据进行更改。map=new Map<String,Object>(data.get(i))获取的是克隆的
            String IsShow = map.get("IsShow").toString();   //是否显示
            String Channel = map.get("Channel").toString();   //通道号
            String State = map.get("State").toString();    //开关状态
            String matrix = map.get("Matrix").toString();  //图标的矩阵参数
            Bitmap bitmap = (Bitmap) map.get("Image");
            // Bitmap bitmap=BitmapFactory.decodeResource(Edit_Activity.this.getResources(),R.drawable.bulb1);
            if (IsShow.equals("1"))   //如果确认要显示
            {
                Matrix matrix1 = null;
                if (!matrix.equals("null")) {
                    String[] s = matrix.split(";");
                    float[] values = new float[9];
                    for (int k = 0; k < 9; k++)
                        values[k] = Float.valueOf(s[k]);
                    matrix1 = new Matrix();
                    matrix1.setValues(values);
                }
                final StickerView sticker1 = new StickerView(Edit_Activity.this);
                sticker1.setEditable(true);
                sticker1.setShowDrawController(false);
                sticker1.setTab(Integer.valueOf(Channel));
                sticker1.setOnClickListener(new View.OnClickListener() {   //点击事件
                    @Override
                    public void onClick(View v) {

                    }
                });
                sticker1.setOnStickerDeleteListener(new StickerView.OnStickerDeleteListener() {   //删除时间
                    @Override
                    public void onDelete() {
                        stickerView.remove(sticker1);
                        map.put("IsShow", "0");
                        ((Adapter) gridview.getAdapter()).notifyDataSetChanged();
                        ViewInit1(false);
                    }
                });
                if (matrix1 != null)
                    sticker1.setMarkMatrix(matrix1);
                sticker1.setWaterMark(bitmap);
                stickerView.add(sticker1);
                map.put("StickerView", sticker1);
            }
        }
        //ViewInit1(true);
        Message msg = new Message();
        msg.what = 1;
        handler1.sendMessage(msg);
    }

    /*
     * 根据stickerView中的标签顺序，重新刷新页面显示
     * @param Isshow刷新完毕后，最上面的StickerView是否显示编辑边框
     */
    private void ViewInit1(boolean Isshow)
    {
        layout.removeAllViews();
        // image.setImageBitmap(bmap);
        layout.addView(image);
        for(StickerView sv:stickerView)
        {
            sv.setShowDrawController(false);
            sv.setEditable(true);
            layout.addView(sv);
        }
        if(Isshow)
            stickerView.get(stickerView.size()-1).setShowDrawController(true);

    }

    class Adapter extends BaseAdapter{

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView==null)
                convertView= LayoutInflater.from(getApplication()).inflate(R.layout.edit_listview, null);
            final Map<String,Object> map=data.get(position);
            final String CID=map.get("CID").toString();
            final String IsShow=map.get("IsShow").toString();
            final String Channel=map.get("Channel").toString();
            final String matrix=map.get("Matrix").toString();
            final TextView tx_channel = (TextView)convertView.findViewById(R.id.tx_channel);
            tx_channel.setText("通道" + map.get("Channel").toString());
           // tx_channel.setOnTouchListener(new TouchListener());
            if(IsShow.equals("0"))
                tx_channel.setTextColor(getResources().getColor(R.color.gray));
            else
                tx_channel.setTextColor(getResources().getColor(R.color.blue));
            tx_channel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (IsShow.equals("1"))   //如果现在正在显示，就给该通道的标签加上编辑边框
                    {
                        //StickerView stick=new StickerView(Edit_Activity.this);
                        for (StickerView sv : stickerView) {
                            sv.setShowDrawController(false);
                            if (String.valueOf(sv.getTab()).equals(Channel)) {
                                sv.setShowDrawController(true);
                            }
                        }

                    } else   //如果没有显示，就显示出来
                    {
                        Bitmap bitmap=BitmapFactory.decodeResource(Edit_Activity.this.getResources(),R.drawable.bulb1);
                        Matrix matrix1=null;
                            if(!matrix.equals("null"))
                            {
                                String[] s=matrix.split(";");
                                float[] values=new float[9];
                                for(int k=0;k<9;k++)
                                    values[k]=Float.valueOf(s[k]);
                                matrix1 = new Matrix();
                                matrix1.setValues(values);
                            }
                            final StickerView sticker1 = new StickerView(Edit_Activity.this);
                            sticker1.setEditable(true);
                            sticker1.setShowDrawController(false);
                            sticker1.setTab(Integer.valueOf(Channel));   //给标签设置通道号
                            sticker1.setOnClickListener(new View.OnClickListener() {   //点击事件
                                @Override
                                public void onClick(View v) {

                                }
                            });
                            sticker1.setOnStickerDeleteListener(new StickerView.OnStickerDeleteListener() {   //删除时间
                                @Override
                                public void onDelete() {
                                    stickerView.remove(sticker1);
                                    map.put("IsShow", "0");
                                    Adapter.this.notifyDataSetChanged();
                                    ViewInit1(false);
                                }
                            });
                            if(matrix1!=null)
                                sticker1.setMarkMatrix(matrix1);
                            sticker1.setWaterMark(bitmap);
                        stickerView.add(sticker1);
                        map.put("IsShow", "1");
                        map.put("StickerView", sticker1);
                        tx_channel.setTextColor(getResources().getColor(R.color.blue));
                        Adapter.this.notifyDataSetChanged();
                        ViewInit1(true);
                    }
                }
            });
            return convertView;
        }
    }
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data2) {
        super.onActivityResult(requestCode, resultCode, data2);
        if (resultCode != RESULT_CANCELED)
        {
            if(requestCode==10000||requestCode==10001)//如果是刚刚选择完，还未裁剪，则跳转到裁剪的activity
            {
                if (data2 != null) {
                    //取得返回的Uri,基本上选择照片的时候返回的是以Uri形式，但是在拍照中有得机子呢Uri是空的，所以要特别注意
                    Uri mImageCaptureUri = data2.getData();
                    //返回的Uri不为空时，那么图片信息数据都会在Uri中获得。如果为空，那么我们就进行下面的方式获取
                    if (mImageCaptureUri != null) {
                        Bitmap bmap_image;
                        try {
                            //这个方法是根据Uri获取Bitmap图片的静态方法
                            bmap_image = MediaStore.Images.Media.getBitmap(getContentResolver(), mImageCaptureUri);
//                            if (image != null) {
//                                startPhotoZoom(mImageCaptureUri);    //打开裁剪activity
//                            }
                            bg_bitmap=bmap_image;
                            //更新页面显示
                            image.setImageBitmap(bmap_image);
                        } catch (Exception e) {
                            Log.i("IBM","URI出错"+e.toString());
                        }
                    }
                    else {
                        Bundle extras = data2.getExtras();
                        Bitmap bmap_image=null;
                        if (extras != null) {
                            //这里是有些拍照后的图片是直接存放到Bundle中的所以我们可以从这里面获取Bitmap图片
                            bmap_image = extras.getParcelable("data");
                        }
                        // 判断存储卡是否可以用，可用进行存储
                        String state = Environment.getExternalStorageState();
                        if (state.equals(Environment.MEDIA_MOUNTED)) {
                            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                            File tempFile = new File(path, "image.jpg");
                            FileOutputStream b = null;
                            try {
                                b = new FileOutputStream(tempFile);
                                bmap_image.compress(Bitmap.CompressFormat.JPEG, 100, b);// 把数据写入文件
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } finally {
                                try {
                                    b.flush();
                                    b.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            bg_bitmap=bmap_image;
                            //更新页面显示
                            image.setImageBitmap(bmap_image);
                            //startPhotoZoom(Uri.fromFile(tempFile));
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "未找到存储卡，无法存储照片！", Toast.LENGTH_SHORT).show();
                        }
                    }

                }
            }
            else   //如果是裁剪后返回，调用的回调方法
            {
                if (data2 != null) {
                    //取得返回的Uri,基本上选择照片的时候返回的是以Uri形式，但是在拍照中有得机子呢Uri是空的，所以要特别注意
                    Uri mImageCaptureUri = data2.getData();
                    //返回的Uri不为空时，那么图片信息数据都会在Uri中获得。如果为空，那么我们就进行下面的方式获取
                    if (mImageCaptureUri != null) {
                        Log.i("IBM","URI不为空"+mImageCaptureUri.toString());
                        Bitmap bmap_image;
                        try {
                            //这个方法是根据Uri获取Bitmap图片的静态方法
                            bmap_image = MediaStore.Images.Media.getBitmap(getContentResolver(), mImageCaptureUri);
                            if (image != null) {
                                bg_bitmap=bmap_image;
//                                DbHelper dh1=new DbHelper(Edit_Activity.this,"IBMS",null,1);
//                                SQLiteDatabase db = dh1.getWritableDatabase();
//                                db.beginTransaction();
//                                ContentValues cv = new ContentValues();    //内容数组
//                                ByteArrayOutputStream os = new ByteArrayOutputStream();
//                                bmap_image.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
//                                cv.put("Image", os.toByteArray());
//                                    db.update("room_tb", cv, "Name=?", new String[]{room});
//
//                                db.setTransactionSuccessful();
//                                db.endTransaction();
//                                db.close();
                                //更新页面显示
                                image.setImageBitmap(bmap_image);

                                // ((Adapter)grid.getAdapter()).notifyDataSetChanged();   //刷新
                            }
                        } catch (Exception e) {
                            Log.i("IBM","URI出错1"+e.toString()+"result__code:"+result_code);
                        }
                    }
                    else {
                        Bundle extras = data2.getExtras();
                        Log.i("IBM",extras.toString());
                        if (extras != null) {
                            //这里是有些拍照后的图片是直接存放到Bundle中的所以我们可以从这里面获取Bitmap图片
                            Bitmap bmap_image = extras.getParcelable("data");
                            if (bmap_image != null) {
                                bg_bitmap=bmap_image;
//                                DbHelper dh1=new DbHelper(Edit_Activity.this,"IBMS",null,1);
//                                SQLiteDatabase db = dh1.getWritableDatabase();
//                                db.beginTransaction();
//                                ContentValues cv = new ContentValues();    //内容数组
//                                ByteArrayOutputStream os = new ByteArrayOutputStream();
//                                bmap_image.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
//                                cv.put("Image", os.toByteArray());
//                                db.update("room_tb", cv, "Name=?", new String[]{room});
//                                db.setTransactionSuccessful();
//                                db.endTransaction();
//                                db.close();
//                              bg_bitmap=bmap_image;
                                //更新页面显示
                                image.setImageBitmap(bmap_image);
                                //((Adapter)grid.getAdapter()).notifyDataSetChanged();   //刷新
                            }
                        }
                    }
                }
            }

        }
        else   //如果点击了取消，则判断是不是裁剪的activity，若是则返回图片选择或拍照的页面，若不是则不进行操作
        {
            if((requestCode==10000||requestCode==10001))//如果是在选择图片中，点击了取消，不进行操作
            {}
            else
            {
                //如果是在裁剪的页面选择了取消，则打开图库
                Intent intent = new Intent(
                        Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,10000); //请求码为10000，  用来区分图片是裁剪前的还是裁剪后的


            }
        }
    }
    /**
     * 裁剪图片方法实现
     * @param uri
     */
    public void startPhotoZoom(Uri uri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        // 设置裁剪
        intent.putExtra("crop", "true");
        intent.putExtra("scale", true);// 去黑边
        intent.putExtra("scaleUpIfNeeded", true);// 去黑边
        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", 3);
        intent.putExtra("aspectY", 4);
        // outputX outputY 是裁剪图片宽高
        intent.putExtra("outputX", convert.Converts.dip2px(getApplicationContext(), 180));
        intent.putExtra("outputY", convert.Converts.dip2px(getApplicationContext(), 240));
        intent.putExtra("return-data", false);
        startActivityForResult(intent, result_code);
    }

}
