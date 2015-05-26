package com.tao.location;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationConfiguration.LocationMode;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeOption;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;

@SuppressLint("InflateParams")
@SuppressWarnings("deprecation")
public class MainActivity extends Activity {
	
	private static final int MSG_TOKEN_GOTTEN = 1;
	private static final int MSG_VCODE_GOTTEN = 2;
	private static final int MSG_POSITION_GOTTEN = 3;
	private static final int MSG_ERROR = 4;
	private static final String FILEPATH = Environment.getExternalStorageDirectory() + File.separator + "Location";
	
	private MapView mMapView = null;
	private BaiduMap mBaiduMap = null;
	
	private LocationClient mLocationClient = null;
	private boolean isFirstLoc = true;
	
	private GeoCoder mGeoCoder = null;
	
	private boolean tokenHasGotten = false;
	private String tokenLw;    //访问http://www.haoservice.com/freeLocation/时获取响应头里面的Set-Cookie
	private String tokenFormForGSM;  //取得http://www.haoservice.com/freeLocation/页面中表单的隐藏输入框的值
	private String tokenFormForCDMA;
	private String sessionId;  //访问http://www.haoservice.com/Handle/ValidateCode.ashx获取响应头里的Set-Cookie
	private String vcode;      //用户通过对话框输入的验证码
	private String address;    //服务器返回的地址信息
	
	private ProgressDialog progressDialog = null;
	
	@SuppressLint("HandlerLeak")
	private Handler handler = new Handler(){
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_TOKEN_GOTTEN:
				tokenHasGotten = true;
				getVCodeImage();
				break;
			case MSG_VCODE_GOTTEN:
				View v = getLayoutInflater().inflate(R.layout.dialog, null);
				ImageView vcodeImage = (ImageView) v.findViewById(R.id.vcodeImg);
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = false;
				Bitmap src = BitmapFactory.decodeFile(FILEPATH+"/vcode.gif", options);
				if (src == null) {
					getVCodeImage();
					return;
				}
				Bitmap output = Bitmap.createScaledBitmap(src, options.outWidth*3, options.outHeight*3, true);
				if (output == null) {
					getVCodeImage();
					return;
				}
				vcodeImage.setImageBitmap(output);
				progressDialog.cancel();
				final EditText vcodeEdit = (EditText) v.findViewById(R.id.vcodeEdit);
				AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).setPositiveButton("确定", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						vcode = vcodeEdit.getText().toString();
						getLatAndLng();
						dialog.cancel();
						progressDialog.show();
					}
				}).create();
				dialog.setView(v);
				dialog.show();
				break;
			case MSG_POSITION_GOTTEN:
				progressDialog.cancel();
				try {
					JSONObject obj = new JSONObject(msg.obj.toString());
					
					int error = obj.getInt("ErrorCode");
					if (error != 0) {
						Toast.makeText(getApplicationContext(), obj.getString("Message"), Toast.LENGTH_SHORT).show();
					} else {
						JSONObject location = obj.getJSONObject("data");
						double lng = location.getDouble("lon");
						double lat = location.getDouble("lat");
						address = location.getString("addr");
						LatLng ptCenter = new LatLng(lat, lng);
						
						mBaiduMap.clear();
						mBaiduMap.addOverlay(new MarkerOptions().position(ptCenter)
								.icon(BitmapDescriptorFactory
										.fromResource(R.drawable.latlng)));
						mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(ptCenter));
						mGeoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(ptCenter));
					}
					
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			case MSG_ERROR:
				Toast.makeText(getApplicationContext(), msg.obj.toString(), Toast.LENGTH_LONG).show();
				break;
			}
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SDKInitializer.initialize(getApplicationContext());
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		
		//地图初始化
		mMapView = (MapView) findViewById(R.id.bmapView);
		mBaiduMap = mMapView.getMap();
		mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
		mBaiduMap.setMapStatus(MapStatusUpdateFactory.zoomTo(16));
		
		//开启定位图层
		mBaiduMap.setMyLocationEnabled(true);
		//定位初始化
		mBaiduMap.setMyLocationConfigeration(new MyLocationConfiguration(LocationMode.NORMAL, true, null));
		mLocationClient = new LocationClient(this);
		mLocationClient.registerLocationListener(new BDLocationListener() {
			public void onReceiveLocation(BDLocation location) {
				if (location == null || mMapView == null) return;
				MyLocationData locData = new MyLocationData.Builder()
						.accuracy(location.getRadius()).latitude(location.getLatitude())
						.longitude(location.getLongitude()).build();
				mBaiduMap.setMyLocationData(locData);
				if (isFirstLoc) {
					isFirstLoc = false;
					LatLng ll = new LatLng(location.getLatitude(),location.getLongitude());
					MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
					mBaiduMap.animateMapStatus(u);
				}
			}
		});
		LocationClientOption option = new LocationClientOption();
		option.setOpenGps(true);
		option.setCoorType("bd09ll");
		option.setScanSpan(1000);
		mLocationClient.setLocOption(option);
		mLocationClient.start();
		
		//搜索初始化（根据地址搜索和根据经纬度搜索）
		mGeoCoder = GeoCoder.newInstance();
		mGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
			public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
				if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
					Toast.makeText(MainActivity.this, "没有找到指定经纬度的相关信息！", Toast.LENGTH_LONG).show();
					return;
				}
				
				mGeoCoder.geocode(new GeoCodeOption().city(result.getAddressDetail().city).address(address));
			}
			public void onGetGeoCodeResult(GeoCodeResult result) {
				if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
					Toast.makeText(MainActivity.this, "没有搜索到指定地址信息！", Toast.LENGTH_LONG).show();
					return;
				}
				
				mBaiduMap.addOverlay(new MarkerOptions().position(result.getLocation())
						.icon(BitmapDescriptorFactory
								.fromResource(R.drawable.location)));
				mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(result.getLocation()));
			}
		});
		
		File path = new File(FILEPATH);
		if (!path.exists()) path.mkdir();
		
		findViewById(R.id.relocate).setOnClickListener(new android.view.View.OnClickListener() {
			public void onClick(View v) {
				int id = v.getId();
				switch (id) {
				case R.id.relocate:
					progressDialog = new ProgressDialog(MainActivity.this);
					progressDialog.setMessage("请稍候...");
					progressDialog.show();
					if (tokenHasGotten)
						getVCodeImage();
					else 
						getToken();
					break;
				}
			}
		});
	}
	
	private void getToken() {
		new Thread(new Runnable(){
			public void run() {
				HttpURLConnection connection = null;
				Message message = new Message();
				try {
					URL url = new URL("http://www.haoservice.com/freeLocation/");
					connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("GET");
					String token = connection.getHeaderField("Set-Cookie");
					int start = token.indexOf("_Lw__") + 6;
					int end = token.indexOf(";", start);
					tokenLw = token.substring(start, end);
					
					BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
					String page = "";
					String line;
					while ((line = reader.readLine()) != null)
						page += line;
					start = page.indexOf("__RequestVerificationToken");
					start = page.indexOf("value=", start) + 7;
					end = page.indexOf("\"", start);
					tokenFormForGSM = page.substring(start, end);
					
					start = page.indexOf("__RequestVerificationToken", end);
					start = page.indexOf("value=", start) + 7;
					end = page.indexOf("\"", start);
					tokenFormForCDMA = page.substring(start, end);
					
					url = new URL("http://www.haoservice.com/Handle/ValidateCode.ashx");
					connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("GET");
					token = connection.getHeaderField("Set-Cookie");
					start = token.indexOf("SessionId=") + 10;
					end = token.indexOf(";", start);
					sessionId = token.substring(start, end);	
					
					message.what = MSG_TOKEN_GOTTEN;
					
				} catch (Exception e) {
					e.printStackTrace();
					message.what = MSG_ERROR;
					message.obj = "获取Token失败"+e.getMessage();
				} finally {
					if (connection != null) connection.disconnect();
					handler.sendMessage(message);
				}
			}
		}).start();
	}
	
	private void getVCodeImage() {
		new Thread(new Runnable(){
			public void run() {
				HttpURLConnection connection = null;
				Message message = new Message();
				try {
					URL url = new URL("http://www.haoservice.com/Handle/ValidateCode.ashx?code="+Math.random());
					connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("GET");
					connection.addRequestProperty("Host", "www.haoservice.com");
					connection.addRequestProperty("Cookie", String.format("%s%s; %s%s",
							"ASP.NET_SessionId=", sessionId,
							"__RequestVerificationToken_Lw__=", tokenLw));
					InputStream is = connection.getInputStream();
					FileOutputStream img = new FileOutputStream(FILEPATH + "/vcode.gif");
					
					byte[] buffer = new byte[2048];
					while (is.read(buffer) != -1) {
						img.write(buffer);
					}
					img.close();
					is.close();
					message.what = MSG_VCODE_GOTTEN;
				} catch (Exception e) {
					e.printStackTrace();
					message.what = MSG_ERROR;
					message.obj = "获取验证码图片失败:"+e.getMessage();
				} finally {
					if (connection != null) connection.disconnect();
					handler.sendMessage(message);
				}
			}
		}).start();
	}
	
	private void getLatAndLng() {
		new Thread(new Runnable(){
			public void run() {
				Message message = new Message();
				try {
					HttpClient client = new DefaultHttpClient();
					
					TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE); 
					int cid = 0, lac = 0;
					List<NameValuePair> urlParameters = null;
					HttpPost post = null;
					if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
						GsmCellLocation location = (GsmCellLocation) tm.getCellLocation();
						cid = location.getCid();
						lac = location.getLac();
						
						post = new HttpPost("http://www.haoservice.com/home/cellapi");
						urlParameters = new ArrayList<NameValuePair>();
						urlParameters.add(new BasicNameValuePair("__RequestVerificationToken", tokenFormForGSM));
						urlParameters.add(new BasicNameValuePair("mcc", "460"));
						urlParameters.add(new BasicNameValuePair("mnc", "00"));
						urlParameters.add(new BasicNameValuePair("lac", Integer.toString(lac)));
						urlParameters.add(new BasicNameValuePair("cid", Integer.toString(cid)));
						urlParameters.add(new BasicNameValuePair("hex", "10"));
						urlParameters.add(new BasicNameValuePair("ValidateCode", vcode));
					} else {
						CdmaCellLocation location = (CdmaCellLocation) tm.getCellLocation();
						lac = location.getNetworkId();
						cid = location.getBaseStationId();
						message.what = MSG_ERROR;
						message.obj = "nid:"+lac+" bid:"+cid;
						handler.sendMessage(message);
						
						post = new HttpPost("http://www.haoservice.com/home/cdmaapi");
						urlParameters = new ArrayList<NameValuePair>();
						urlParameters.add(new BasicNameValuePair("__RequestVerificationToken", tokenFormForCDMA));
						urlParameters.add(new BasicNameValuePair("mcc", "460"));
						urlParameters.add(new BasicNameValuePair("sid", "14175"));
						urlParameters.add(new BasicNameValuePair("nid", Integer.toString(lac)));
						urlParameters.add(new BasicNameValuePair("bid", Integer.toString(cid)));
						urlParameters.add(new BasicNameValuePair("hex", "10"));
						urlParameters.add(new BasicNameValuePair("ValidateCode", vcode));
					}
					
					post.setHeader("Host", "www.haoservice.com");
					post.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
					post.setHeader("Cookie", String.format("%s%s; %s%s",
							"ASP.NET_SessionId=", sessionId,
							"__RequestVerificationToken_Lw__=", tokenLw));
					
					
					post.setEntity(new UrlEncodedFormEntity(urlParameters));
					HttpResponse response = client.execute(post);
					
					BufferedReader in = new BufferedReader(
	                        new InputStreamReader(response.getEntity().getContent()));
					
					String inputLine;
					StringBuilder sb = new StringBuilder();
					
					while ((inputLine = in.readLine()) != null)
						sb.append(inputLine);
					in.close();
					
					message.what = MSG_POSITION_GOTTEN;
					message.obj = sb.toString();
					
				} catch (Exception e) {
					e.printStackTrace();
					message.what = MSG_ERROR;
					message.obj = "获取经纬度失败"+e.getMessage();
				} finally {
					handler.sendMessage(message);
				}
			}
		}).start();
	}
	
	public static void writeToLog(String msg) {
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA);
		String now = dateFormat.format(new Date());
		try {
			File file = new File(FILEPATH + "/log.txt");
			FileWriter fileWriter = new FileWriter(file, true);
			fileWriter.write(now + "-" + msg + "\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mMapView.onDestroy();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mMapView.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		mMapView.onPause();
	}

}
