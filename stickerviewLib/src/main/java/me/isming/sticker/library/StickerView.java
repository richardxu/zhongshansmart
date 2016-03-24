package me.isming.sticker.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;


/**
 * Created by sam on 14-8-14.
 */
public class StickerView extends View {

    public int tab;    //这个标签view 的标号（例如可以对应开关的通道号）
    private float mScaleSize;

    public static final float MAX_SCALE_SIZE = 50.2f;   //缩放范围，最大比例
    public static final float MIN_SCALE_SIZE = 0.01f;   //缩放范围，最小比例

    private boolean mState;   //开关状态,true表示开，false表示关

    private float[] mOriginPoints;
    private float[] mPoints;
    private RectF mOriginContentRect;
    private RectF mContentRect;
    private RectF mViewRect;

    private float mLastPointX, mLastPointY;
    private Context mContext;
    private Bitmap mBitmap;   //主bitmap
    private Bitmap mBitmap_original;   //未经处理的主bitmap
    private Bitmap mControllerBitmap, mDeleteBitmap;   //旋转和删除bitmap
    private Bitmap mReversalHorBitmap,mReversalVerBitmap;//水平反转和垂直反转bitmap
    private Bitmap mStatusBitmap;
    private float mStatusWidth,mStatusHeight;
    private Matrix mMatrix=null;   //主bitmap的矩阵参数
    private Paint mPaint, mBorderPaint;
    private float mControllerWidth, mControllerHeight, mDeleteWidth, mDeleteHeight;
    private float mReversalHorWidth,mReversalHorHeight,mReversalVerWidth,mReversalVerHeight;//分别是4个边角图片的宽度和高度
    private boolean mInController, mInMove;  //缩放模式，拖动模式
    private boolean mInReversalHorizontal,mInReversalVertical;   //横向对称变换模式，纵向对称变换模式

    private boolean mDrawController = false;    //是否显示操作边框
    private boolean mEditable=true;     //是否可编辑。如果为false，则不会出现操作边框
    //private boolean mCanTouch;
    private float mStickerScaleSize = 1.0f;   //当前缩放比例
   // private float oldlength=0;


    private OnStickerDeleteListener mOnStickerDeleteListener;

    public StickerView(Context context) {

        this(context, null);
        mContext=context;
    }

    public StickerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mContext=context;
    }

    public StickerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext=context;
        init();   //执行初始化方法
    }

    public int getTab()
    {
        return tab;
    }
    public void setTab(int t)
    {
        tab=t;
    }

    public void setState(boolean state)
    {
        mState=state;

    }
    public boolean getState()
    {
        return mState;

    }

    private void init() {  //初始化画笔和图片信息

        mPaint = new Paint();   //画笔
        mPaint.setAntiAlias(true);  //消除锯齿
        mPaint.setFilterBitmap(true);
        mPaint.setStyle(Paint.Style.STROKE);   //实心
        mPaint.setStrokeWidth(4.0f);   //宽度为4px
        mPaint.setColor(Color.WHITE);   //颜色为白色

        mBorderPaint = new Paint(mPaint);    //边框线的画笔
        mBorderPaint.setColor(Color.parseColor("#B2ffffff"));
        mBorderPaint.setShadowLayer(DisplayUtil.dip2px(getContext(), 2.0f), 0, 0, Color.parseColor("#33000000"));

        mControllerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sticker_control);
        mControllerWidth = mControllerBitmap.getWidth();
        mControllerHeight = mControllerBitmap.getHeight();

        mDeleteBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sticker_delete);
        mDeleteWidth = mDeleteBitmap.getWidth();
        mDeleteHeight = mDeleteBitmap.getHeight();



//        mReversalHorBitmap = BitmapFactory.decodeResource(getResources(),R.drawable.ic_sticker_reversal_horizontal);
//        mReversalHorWidth = mReversalHorBitmap.getWidth();
//        mReversalHorHeight = mReversalHorBitmap.getHeight();
//
//        mReversalVerBitmap = BitmapFactory.decodeResource(getResources(),R.drawable.ic_sticker_reversal_vertical);
//        mReversalVerWidth = mReversalVerBitmap.getWidth();
//        mReversalVerHeight = mReversalVerBitmap.getHeight();

    }

    public void setWaterMark(@NonNull Bitmap bitmap) {   //设置水印图片
        mBitmap_original=bitmap;
        mBitmap = bitmap;
        mStickerScaleSize = 1.0f;


        setFocusable(true);
        try {

            float px = dip2px(mContext, 100);
            float py = dip2px(mContext, 100);
            // float px = mBitmap.getWidth();   //图片宽度
            //float py = mBitmap.getHeight();    //图片高度
            //mOriginPoints = new float[]{px, py, px + bitmap.getWidth(), py, bitmap.getWidth() + px, bitmap.getHeight() + py, px, py + bitmap.getHeight()};
            mOriginPoints = new float[]{0 , 0 , px , 0, px , py, 0 , py, px / 2, py / 2};  //5个点的坐标，分别是左上角，右上角，右下角，左下角，图片  5个图标的中心点坐标
            mOriginContentRect = new RectF(0, 0, px, py);  //这是包含整个内容的矩形框
            mPoints = new float[10];   //这里存储经过矩阵变换后的各个点的坐标值
            mContentRect = new RectF();   //这里保存经过矩阵变换后整个view的矩形框，在onDraw中进行
            if (mMatrix == null) {
                mMatrix = new Matrix();
                float transtLeft = ((float) DisplayUtil.getDisplayWidthPixels(getContext()) - px) / 2;
                float transtTop = ((float) DisplayUtil.getDisplayWidthPixels(getContext()) - py) / 2;

                mMatrix.postScale(0.5f, 0.5f, mPoints[8], mPoints[9]);    //先缩小为二分之一
                mMatrix.postTranslate(transtLeft, transtTop);   //平移到初始化位置，横向在屏幕中央，纵向在中央偏上（left与top相等）
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        postInvalidate();    //界面更新，重新执行onDraw

    }

    public Matrix getMarkMatrix() {  //获取图片参数矩阵
        return mMatrix;
    }

    public void setMarkMatrix(Matrix matrix)   //设置图片的参数矩阵
    {
        mMatrix = new Matrix(matrix);
    }

    @Override
    public void setFocusable(boolean focusable) {     //设置是否获取焦点，若不获取，则不显示边框
        super.setFocusable(focusable);
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {    //重写onDraw方法，画出图片和边框以及边框上的图标
        super.onDraw(canvas);   //canvas是画布，整体就是一张画布
        if (mBitmap == null || mMatrix == null) {
            return;
        }
        mBitmap = Bitmap.createScaledBitmap(mBitmap, dip2px(mContext, 100), dip2px(mContext, 100), true);  //先缩放到标准大小
        mMatrix.mapPoints(mPoints, mOriginPoints);   //将mMatrix应用到mOriginPoints后得到新的坐标点，存在mPoints中。下同
        mMatrix.mapRect(mContentRect, mOriginContentRect);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));    //消除锯齿
        canvas.drawBitmap(mBitmap, mMatrix, mPaint);  //画图片，中间的大图。三个参数依次是位图、矩阵参数、画笔
        if (mDrawController && isFocusable() && mEditable) {
            canvas.drawLine(mPoints[0]-20, mPoints[1]-20, mPoints[2]+20, mPoints[3]-20, mBorderPaint);   //画线，前四个参数分别为(x0,y0),(x1,y1)代表线的起始两个点
            canvas.drawLine(mPoints[2]+20, mPoints[3]-20, mPoints[4]+20, mPoints[5]+20, mBorderPaint);
            canvas.drawLine(mPoints[4]+20, mPoints[5]+20, mPoints[6]-20, mPoints[7]+20, mBorderPaint);
            canvas.drawLine(mPoints[6]-20, mPoints[7]+20, mPoints[0]-20, mPoints[1]-20, mBorderPaint);
            canvas.drawBitmap(mControllerBitmap, mPoints[4] +20- mControllerWidth / 2, mPoints[5]+20 - mControllerHeight / 2, mBorderPaint);  //画图片   右下角旋转按钮
            canvas.drawBitmap(mDeleteBitmap, mPoints[0]-20 - mDeleteWidth / 2, mPoints[1]-20 - mDeleteHeight / 2, mBorderPaint);    //左上角删除按钮
//            canvas.drawBitmap(mReversalHorBitmap,mPoints[2]-mReversalHorWidth/2,mPoints[3]-mReversalVerHeight/2,mBorderPaint);    //右上角垂直翻转按钮
//            canvas.drawBitmap(mReversalVerBitmap,mPoints[6]-mReversalVerWidth/2,mPoints[7]-mReversalVerHeight/2,mBorderPaint);     //左下角水平翻转按钮
        }
        if (!mEditable)   //如果是不可编辑状态，显示右上角的开关状态
        {
            if(mState)
                mStatusBitmap=BitmapFactory.decodeResource(getResources(),R.drawable.ondot);
            else
                mStatusBitmap=BitmapFactory.decodeResource(getResources(),R.drawable.offdot);
            mStatusWidth=mStatusBitmap.getWidth();
            mStatusHeight=mStatusBitmap.getHeight();
            canvas.drawBitmap(mStatusBitmap, mPoints[2]+8- mStatusWidth / 2, mPoints[3]-8- mStatusHeight / 2, mBorderPaint);    //右上角开关状态按钮

        }
    }

    public Bitmap getBitmap() {
//        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
//        Canvas canvas = new Canvas(bitmap);
//        mDrawController = false;
//        draw(canvas);
//        mDrawController = true;
//        canvas.save();
//        return bitmap;
        return mBitmap_original;
    }

    public void setShowDrawController(boolean show) {
        mDrawController = show;
        postInvalidate();
    }

    public void setEditable(boolean editable){
        mEditable=editable;
        postInvalidate();
    }

    public boolean getEditable() {
        return mEditable;
    }

    //判断点击区域是否在旋转、缩放按钮区域内
    private boolean isInController(float x, float y) {
        int position = 4;
        //while (position < 8) {
            float rx = mPoints[position]+20;
            float ry = mPoints[position + 1]+20;  //缩放按钮中点坐标
            RectF rectF = new RectF(rx - mControllerWidth / 2,
                    ry - mControllerHeight / 2,
                    rx + mControllerWidth / 2,
                    ry + mControllerHeight / 2);  //绘制矩形，缩放按钮区域
            if (rectF.contains(x, y)) {
                return true;
            }
         //   position += 2;
        //}
        return false;

    }

    //判断点击区域是否在删除按钮区域内
    private boolean isInDelete(float x, float y) {
        int position = 0;
        //while (position < 8) {
        float rx = mPoints[position]-20;
        float ry = mPoints[position + 1]-20;
        RectF rectF = new RectF(rx - mDeleteWidth / 2,
                ry - mDeleteHeight / 2,
                rx + mDeleteWidth / 2,
                ry + mDeleteHeight / 2);
        if (rectF.contains(x, y)) {
            return true;
        }
        //   position += 2;
        //}
        return false;

    }
    //判断点击区域是否在水平反转按钮区域内
    private boolean isInReversalHorizontal(float x,float y){
        int position = 2;
        float rx = mPoints[position];
        float ry = mPoints[position+1];

        RectF rectF = new RectF(rx - mReversalHorWidth/2,ry-mReversalHorHeight/2,rx+mReversalHorWidth/2,ry+mReversalHorHeight/2);
        if (rectF.contains(x,y))
            return true;

        return false;

    }
    //判断点击区域是否在垂直反转按钮区域内
    private boolean isInReversalVertical(float x,float y){
        int position = 6;
        float rx = mPoints[position];
        float ry = mPoints[position+1];

        RectF rectF = new RectF(rx - mReversalVerWidth/2,ry - mReversalVerHeight/2,rx + mReversalVerWidth/2,ry+mReversalVerHeight/2);
        if (rectF.contains(x,y))
            return true;
        return false;
    }

    private boolean mInDelete = false;
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {   //事件分发
        Log.i("Event", "dispathTouchEvent  事件分发" + String.valueOf(event.getAction()));
        if (!isFocusable()) {
           // Log.i("Event", "无焦点");
            return super.dispatchTouchEvent(event);
        }

        if (mViewRect == null) {
            mViewRect = new RectF(0f, 0f, getMeasuredWidth(), getMeasuredHeight());
        }
        float x = event.getX();
        float y = event.getY();
//        if(!mContentRect.contains(x, y)) {
//            Log.i("Event", "手指出界");
//            return false;   //在拖动过程中，如果标签不可编辑，且手指不在图标范围内，则不再接收事件
//
//        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:   //有手指按下
                if (isInController(x, y)) {    //缩放旋转

                    mInController = true;
                    mLastPointY = y;
                    mLastPointX = x;
                    break;
                }

                if (isInDelete(x, y)) {   //删除

                    mInDelete = true;
                    break;
                }

               /* if (isInReversalHorizontal(x, y)) {    //横向翻转
                    mInReversalHorizontal = true;
                    break;
                }

                if (isInReversalVertical(x, y)) {   //纵向翻转
                    mInReversalVertical = true;
                    break;
                }*/

                if (mContentRect.contains(x, y)) {    //拖动

                    mLastPointY = y;
                    mLastPointX = x;
                    mInMove = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isInDelete(x, y) && mInDelete &&mDrawController) {
                    doDeleteSticker();    //执行删除操作，删除了这个view就没了，所以变量什么的也不清零了
                    break;
                }
                else {
                    mLastPointX = 0;
                    mLastPointY = 0;
                    mInController = false;
                    mInMove = false;
                    mInDelete = false;
                    return super.dispatchTouchEvent(event);

                }
               /* if (isInReversalHorizontal(x, y) && mInReversalHorizontal) {
                    doReversalHorizontal();   //执行横向翻转
                    break;
                }
                if (isInReversalVertical(x, y) && mInReversalVertical) {
                    doReversalVertical();    //执行纵向翻转
                    break;
                }*/
            case MotionEvent.ACTION_CANCEL:   //出发了ACTION_CANCEL操作，所有的置0
                mLastPointX = 0;
                mLastPointY = 0;
                mInController = false;
                mInMove = false;
                mInDelete = false;
                break;


            case MotionEvent.ACTION_MOVE:

                if (mInController&&mDrawController) {  //缩放旋转的操作

                    mMatrix.postRotate(rotation(event), mPoints[8], mPoints[9]);  //围绕图片中心点旋转
                    float lastLenght = caculateLength(mLastPointX, mLastPointY);  //上次手指到图片中心的距离
                    float touchLenght = caculateLength(event.getX(), event.getY());      //手指到图片中心的距离
                    if (FloatMath.sqrt((lastLenght - touchLenght) * (lastLenght - touchLenght)) > 0.0f) {   //据说是防止长茧的手指有无操作
                        float scale = touchLenght / lastLenght;
                        float nowsc = mStickerScaleSize * scale;
                        if (nowsc >= MIN_SCALE_SIZE && nowsc <= MAX_SCALE_SIZE) {
                            mMatrix.postScale(scale, scale, mPoints[8], mPoints[9]);   //对图片进行缩放
                            mStickerScaleSize = nowsc;    //记录下缩放比例
                        }
                    }

                    invalidate();
                    mLastPointX = x;
                    mLastPointY = y;
                    break;

                }

                if (mInMove == true&&mDrawController) { //拖动的操作
                    float cX = x - mLastPointX;
                    float cY = y - mLastPointY;
                    mInController = false;
                    //Log.i("MATRIX_OK", "ma_jiaodu:" + a(cX, cY));

                    if (FloatMath.sqrt(cX * cX + cY * cY) > 2.0f && canStickerMove(cX, cY)) {
                        //Log.i("MATRIX_OK", "is true to move");
                        mMatrix.postTranslate(cX, cY);
                        postInvalidate();
                        mLastPointX = x;
                        mLastPointY = y;
                    }
                    break;
                }
                default:break;


                //return false;

        }
        if(mInController==false && mInMove==false &&mInDelete==false)  //如果没有点击到图片，不进行任何处理，并且不处理此后的事件（ACTION_MOVE，ACTION_UP）
        {
            this.setShowDrawController(false);
            postInvalidate();
            return false;  //不会触发onClick
        }
        else  //如果点击到了这张图片，则继续后面的操作，可以触发onClick
        {

            if(mEditable)   //如果是可编辑模式，就显示边框
            {
                this.setShowDrawController(true);
                postInvalidate();
            }
            return super.dispatchTouchEvent(event);   //返回这个，调用父类方法，事件才会继续向下分发。返回true会接收后续MOVE,UP，返回false则后续事件不再接收

        }//else
        //return true;   //若不执行父类的dispatchTouchEvent方法，直接返回true或false，则事件不再向下分发，且不会触发onTouch、onClick等后续事件

    }


    private void doDeleteSticker() {
        setWaterMark(null);
        if (mOnStickerDeleteListener != null) {
            mOnStickerDeleteListener.onDelete();
        }
    }

    //图片水平反转
    private void doReversalHorizontal(){
        float[] floats = new float[] { -1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f };
        Matrix tmpMatrix = new Matrix();
        tmpMatrix.setValues(floats);
        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(),
                mBitmap.getHeight(), tmpMatrix, true);
        invalidate();
        mInReversalHorizontal = false;
    }
    //图片垂直反转
    private void doReversalVertical(){
        float[] floats = new float[] { 1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f };
        Matrix tmpMatrix = new Matrix();
        tmpMatrix.setValues(floats);
        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(),
                mBitmap.getHeight(), tmpMatrix, true);
        invalidate();
        mInReversalVertical = false;
    }


    private boolean canStickerMove(float cx, float cy) {   //判断图片是否可以移动，如果手指的坐标在图片的区域内，就可以移动
        float px = cx + mPoints[8];
        float py = cy + mPoints[9];
        if (mViewRect.contains(px, py)) {
            return true;
        } else {
            return false;
        }
    }


    private float caculateLength(float x, float y) {  //计算这个坐标到图片中点的长度
        float ex = x - mPoints[8];
        float ey = y - mPoints[9];
        return FloatMath.sqrt(ex*ex + ey*ey);
    }


    private float rotation(MotionEvent event) {   //旋转角度
        float  originDegree = calculateDegree(mLastPointX, mLastPointY);
        float nowDegree = calculateDegree(event.getX(), event.getY());
        return nowDegree - originDegree;   //计算出要旋转的角度
    }

    private float calculateDegree(float x, float y) {   //计算角度
        double delta_x = x - mPoints[8];
        double delta_y = y - mPoints[9];
        double radians = Math.atan2(delta_y, delta_x);
        return (float) Math.toDegrees(radians);   //
    }

    public interface OnStickerDeleteListener {   //删除接口，可继承此接口重写onDetlet方法，执行删除图标后的操作
        public void onDelete();
    }

    public void setOnStickerDeleteListener(OnStickerDeleteListener listener) {
        mOnStickerDeleteListener = listener;
    }

    //dip转换成像素px
    public int dip2px(Context context, float dipValue){
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int)(dipValue * scale + 0.5f);
    }
}
