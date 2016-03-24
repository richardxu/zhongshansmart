package com.suntrans.zhongshui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import views.TouchListener;

/*
服务器端口配置页面
 */
public class ServerConfig_Activity extends Activity {
	private EditText et_ip,et_port;
	private Button confirm;
	private String serverip;
	private int serverport;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		setContentView(R.layout.serverconfig);     //设置布局文件
		et_ip=(EditText)findViewById(R.id.ip);
		et_port=(EditText)findViewById(R.id.port);
		confirm=(Button)findViewById(R.id.confirm);
        LinearLayout back = (LinearLayout) findViewById(R.id.back);
        back.setOnTouchListener(new TouchListener());
        back.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
		SharedPreferences sharedPreferences= getSharedPreferences("data", Activity.MODE_PRIVATE);       
	    serverip =sharedPreferences.getString("serverip", "-1");   //读取服务器ip，若没有则是-1
	    serverport=Integer.valueOf(sharedPreferences.getString("port", "8082"));
	    
	    et_ip.setText(serverip+"");
	    et_ip.setSelection(serverip.length());
	    et_port.setText(String.valueOf(serverport));
	    
	    confirm.setOnTouchListener(new TouchListener());
	    confirm.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				SharedPreferences sharedPreferences= getSharedPreferences("data", Activity.MODE_PRIVATE); 
				 //实例化SharedPreferences.Editor对象（第二步） 
		        SharedPreferences.Editor editor = sharedPreferences.edit(); 
		        //用putString的方法保存数据 
		        editor.putString("serverip", et_ip.getText().toString());    //服务器IP
		        editor.putString("port", et_port.getText().toString());    //端口
		        //提交当前数据 
		        editor.commit(); 
		        
		        Toast.makeText(getApplicationContext(), "修改成功，重启应用后生效！", Toast.LENGTH_SHORT).show();
			}});
	}
	@Override           //menu选项监听
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		if(item.getItemId() == android.R.id.home)    //如果按下的是返回键
	    {
	        finish();
	        return true;
	    }
		
		else
			return true;
	}
	
}
