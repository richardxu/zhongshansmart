package com.suntrans.zhongshui;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import database.DbHelper;


public class Welcome_Activity extends Activity {
	private String clientip;    //开关的ip地址
	private DatagramSocket UDPclient;       //UDP客户端
	private int isFinish = 0;   //数据初始化是否完成，0代表没完成

	//跳转到主activity
	private Handler handler1=new Handler(){
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			Intent intent = new Intent();
			intent.putExtra("clientip", clientip);             //点击的区域
			intent.setClass(Welcome_Activity.this, Main_Activity.class);//设置要跳转的activity
			startActivity(intent);//开始跳转
			finish();
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.welcome);      //设置activity布局文件
		//UDPInit();   //发送UDP命令，获取开关ip地址
		new Thread() {
			@Override
			public void run() {
				DbInit();       //初始化数据库

			}
		}.start();
		new Thread(){
			@Override
			public void run(){
				try{Thread.sleep(1000);}
				catch(Exception ex){ex.printStackTrace();}
				while(isFinish==0)   //如果没完成，就继续等待
				{
					try{Thread.sleep(1000);}
					catch(Exception ex){ex.printStackTrace();}
				}
				Message msg=new Message();
				handler1.sendMessage(msg);
			}
		}.start();
//		new Handler().postDelayed(new Runnable() {
//
//			public void run() {
//				// TODO Auto-generated method stub
//				if (isFinish == 1) {
//					Intent intent = new Intent();
//					intent.putExtra("clientip", clientip);             //点击的区域
//					intent.setClass(Welcome_Activity.this, Main_Activity.class);//设置要跳转的activity
//					startActivity(intent);//开始跳转
//					finish();
//				} else {
//					new Handler().postDelayed(new Runnable() {
//
//						public void run() {
//							// TODO Auto-generated method stub
//
//							Intent intent = new Intent();
//							intent.putExtra("clientip", clientip);             //点击的区域
//							intent.setClass(Welcome_Activity.this, Main_Activity.class);//设置要跳转的activity
//							startActivity(intent);//开始跳转
//							finish();
//
//
//						}
//
//					}, 2000);   //再延时2秒打开主页面
//				}
//			}
//
//		}, 2500);   //延时2.5秒打开主页面

	}

	public void UDPInit()   //UDP初始化
	{
		new Thread() {
			public void run() {
				try {

					//首先创建一个DatagramSocket对象
					UDPclient = new DatagramSocket();
					//创建一个InetAddree
					InetAddress serverAddress = InetAddress.getByName("255.255.255.255");
					String str = "123456AT+QMAC";
					byte data[] = str.getBytes();
					//创建一个DatagramPacket对象，并指定要讲这个数据包发送到网络当中的哪个地址，以及端口号
					DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, 988);
					//调用socket对象的send方法，发送数据
					UDPclient.send(packet);
					new UDPServerThread().start();      //创建新的线程监听UDP客户端返回的数据

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
	}

	public class UDPServerThread extends Thread    //新建线程接收UDP回应
	{

		public UDPServerThread() {
		}

		public void run() {
			//tvRecv.setText("start");
			byte[] buffer = new byte[1024];
			final StringBuilder sb = new StringBuilder();
			try {

				while (UDPclient != null) {
					// 接收服务器信息       定义输入流
					byte data[] = new byte[1024];
					//创建一个空的DatagramPacket对象
					DatagramPacket packet = new DatagramPacket(data, data.length);
					//使用receive方法接收客户端所发送的数据
					UDPclient.receive(packet);
					clientip = packet.getAddress().toString().replace("/", "");    //ip地址
					String clientmac = new String(packet.getData()).replace("+OK=", "");  //MAC地址
					clientmac = clientmac.replaceAll("\r|\n", "");    //去掉换行符
					clientmac = clientmac.replace(" ", "");   //去掉空格
					clientmac = clientmac.substring(8, 12);   //取出mac地址的最后四位
					//将ip地址保存到文件中
					//实例化SharedPreferences对象（第一步）
					SharedPreferences mySharedPreferences = getSharedPreferences("data", Activity.MODE_PRIVATE);
					//实例化SharedPreferences.Editor对象（第二步）
					SharedPreferences.Editor editor = mySharedPreferences.edit();
					//用putString的方法保存数据
					editor.putString("clientip", clientip);
					//提交当前数据
					editor.commit();
				}

			} catch (Exception e) {
				e.printStackTrace();

			}
		}
	}

	private void DbInit()   //初始化数据库
	{
		//将服务器ip地址和端口保存到文件中
		//实例化SharedPreferences对象（第一步）
		SharedPreferences sharedPreferences= getSharedPreferences("data", Activity.MODE_PRIVATE);
		String serverip =sharedPreferences.getString("serverip", "-1");   //读取服务器ip，若没有则是-1
		if(serverip.equals("-1"))   //如果没有保存服务器ip，则写入服务器ip和端口
		{
			//实例化SharedPreferences.Editor对象（第二步）
			SharedPreferences.Editor editor = sharedPreferences.edit();
			//用putString的方法保存数据
			editor.putString("serverip", "61.235.65.160");    //服务器IP
			editor.putString("port", "8085");    //端口
			//提交当前数据
			editor.commit();
		}
		DbHelper dh1 = new DbHelper(Welcome_Activity.this, "IBMS", null, 1);
		SQLiteDatabase db = dh1.getWritableDatabase();
		db.beginTransaction();
		//获取房间图标的图片
		Bitmap bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.room);
		bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.bulb);
		//获取图片输出流
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
		Cursor cursor = db.query(true, "switchs_tb", new String[]{"CID", "Name"}, null, null, null, null, null, null, null);
		if (cursor.getCount() < 1)  //如果开关表中没有数据，则添加
		{
			long row = 0;
			//ContentValues[] cv = new ContentValues[10];    //内容数组
			ContentValues cva = new ContentValues();
			////////第一个开关///////////
			//第一个开关，第五个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_5);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 5);
			cva.put("Area", "前台");
			cva.put("Name", "前台厅灯1");
			cva.put("VoiceName", "大厅灯槽");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第二个开关，第五个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_5);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 5);
			cva.put("Area", "前台");
			cva.put("Name", "前台厅灯2");
			cva.put("VoiceName", "大厅筒灯");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第一个开关，第四个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_4);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 4);
			cva.put("Area", "接待室");
			cva.put("Name", "接待室灯1");
			cva.put("VoiceName", "大厅灯槽");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第二个开关，第四个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_4);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 4);
			cva.put("Area", "接待室");
			cva.put("Name", "接待室灯2");
			cva.put("VoiceName", "大厅筒灯");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库


			//第一个开关，第二个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_2);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 2);
			cva.put("Area", "接待室");
			cva.put("Name", "接待室空调");
			cva.put("VoiceName", "大厅灯槽");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第一个开关，第六个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_6);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 6);
			cva.put("Area", "办公区");
			cva.put("Name", "办公区灯1");
			cva.put("VoiceName", "大厅筒灯");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库


			//第二个开关，第六个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_6);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 6);
			cva.put("Area", "办公区");
			cva.put("Name", "办公区灯2");
			cva.put("VoiceName", "投影仪插座");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库


			//第一个开关，第七个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_7);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 7);
			cva.put("Area", "办公区");
			cva.put("Name", "办公区灯3");
			cva.put("VoiceName", "侧门插座");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			///////第二个开关//////
			//第二个开关，第一个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_1);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 1);
			cva.put("Area", "办公区");
			cva.put("Name", "办公区空调");
			cva.put("VoiceName", "办公室灯");    //语音名称
			cva.put("Image", os.toByteArray());    //图片转换成byte数组存储
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第一个开关，第十个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_10);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 10);
			cva.put("Area", "总经理办公室");
			cva.put("Name", "总经理室灯1");
			cva.put("VoiceName", "应急灯");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "0");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第一个开关，第一个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_1);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");   //可以进行控制。=0则代表不可以进行控制
			cva.put("MainAddr", Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 1);
			cva.put("Area", "总经理办公室");
			cva.put("Name", "总经理室空调");
			cva.put("VoiceName", "大厅灯槽");    //语音名称
			cva.put("Image", os.toByteArray());    //图片转换成byte数组存储
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库


			//第一个开关，第九个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_9);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 9);
			cva.put("Area", "财务室");
			cva.put("Name", "财务室灯1");
			cva.put("VoiceName", "办公室插座");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "0");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第二个开关，第七个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_7);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 7);
			cva.put("Area", "财务室");
			cva.put("Name", "财务室灯2");
			cva.put("VoiceName", "未配置");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库


			//第二个开关，第二个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_2);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 2);
			cva.put("Area", "财务室");
			cva.put("Name", "财务室空调");
			cva.put("VoiceName", "墙面射灯");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第一个开关，第八个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon1_8);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 8);
			cva.put("Area", "会议室");
			cva.put("Name", "会议室灯1");
			cva.put("VoiceName", "服务台插座");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "0");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库


			//第一个开关，第三个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.socket);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0001");            //开关地址
			cva.put("Channel", 3);
			cva.put("Area", "办公区财务室");
			cva.put("Name", "已受控插座");
			cva.put("VoiceName", "大厅主灯");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第二个开关，第三个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.socket);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 3);
			cva.put("Area", "前台");
			cva.put("Name", "已受控插座");
			cva.put("VoiceName", "卫生间灯");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "1");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库






			//第二个开关，第八个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_8);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 8);
			cva.put("Area", "待配置通道");
			cva.put("Name", "未配置");
			cva.put("VoiceName", "未配置");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "0");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第二个开关，第九个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_8);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 9);
			cva.put("Area", "待配置通道");
			cva.put("Name", "未配置");
			cva.put("VoiceName", "未配置");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "0");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库

			//第二个开关，第十个通道
			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.icon2_8);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Room", "外间");
			cva.put("State", "0");
			cva.put("Editable","1");
			cva.put("MainAddr",  Address.addr_out);   //第六感官地址
			cva.put("RSAddr", "0002");            //开关地址
			cva.put("Channel", 10);
			cva.put("Area", "待配置通道");
			cva.put("Name", "未配置");
			cva.put("VoiceName", "未配置");
			cva.put("Image", os.toByteArray());
			cva.put("IsShow", "0");     //是否显示图片
			cva.put("Matrix", "null");     //图片参数矩阵，为null表示还没有进行设置
			row = db.insert("switchs_tb", null, cva);  //将数据添加到数据库


			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.room_front);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Name", "前台");    //房间名称
			cva.put("RSAddr", Address.addr_out);     //房间内第六感官地址
			cva.put("Image", os.toByteArray());   //房间背景图片
			row = db.insert("room_tb", null, cva);

			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.room_reception);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Name", "接待室");    //房间名称
			cva.put("RSAddr", Address.addr_out);     //房间内第六感官地址
			cva.put("Image", os.toByteArray());   //房间背景图片
			row = db.insert("room_tb", null, cva);

			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.room_office);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Name", "办公区");    //房间名称
			cva.put("RSAddr", Address.addr_out);     //房间内第六感官地址
			cva.put("Image", os.toByteArray());   //房间背景图片
			row = db.insert("room_tb", null, cva);

			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.room_manager);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Name", "总经理办公室");    //房间名称
			cva.put("RSAddr", Address.addr_out);     //房间内第六感官地址
			cva.put("Image", os.toByteArray());   //房间背景图片
			row = db.insert("room_tb", null, cva);

			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.room_finance);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Name", "财务室");    //房间名称
			cva.put("RSAddr", Address.addr_out);     //房间内第六感官地址
			cva.put("Image", os.toByteArray());   //房间背景图片
			row = db.insert("room_tb", null, cva);

			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.room_meeting);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Name", "会议室");    //房间名称
			cva.put("RSAddr", Address.addr_out);     //房间内第六感官地址
			cva.put("Image", os.toByteArray());   //房间背景图片
			row = db.insert("room_tb", null, cva);

			bmp_room = BitmapFactory.decodeResource(Welcome_Activity.this.getResources(), R.drawable.reserve);
			os = new ByteArrayOutputStream();
			bmp_room.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
			cva = new ContentValues();
			cva.put("Name", "待配置通道");    //房间名称
			cva.put("RSAddr", Address.addr_out);     //房间内第六感官地址
			cva.put("Image", os.toByteArray());   //房间背景图片
			row = db.insert("room_tb", null, cva);
//			cva = new ContentValues();
//			cva.put("Name", "里间");
//			cva.put("RSAddr", Address.addr_in);
//			cva.put("Image", os.toByteArray());
//			row = db.insert("room_tb", null, cva);
		}
		db.setTransactionSuccessful();
		db.endTransaction();
		db.close();
		isFinish = 1;
	}
}















