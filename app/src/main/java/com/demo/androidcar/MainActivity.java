package com.demo.androidcar;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.demo.androidcar.constant.Command;
import com.demo.androidcar.constant.DirectionState;
import com.demo.androidcar.util.MusicService;
import com.demo.androidcar.util.MusicService.MyBinder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements
		WifiStateReceiver.BRInteraction  ,CameraManager.CameraManagerListener, SurfaceHolder.Callback, ConnectionManager.ConnectionListener, ConnectionManager.ControllerCommandListener, ConnectionManager.SendCommandListener {

//	private Button button_send = null, button_play = null, button_pause = null;
//	private EditText et_ip = null;
//	private EditText et_port = null;
//	private EditText et_conent = null;
//	private TextView tv_history = null, et = null;
//	private CheckBox checkBoxSwitch = null;

	private TextView tv_ip_view;
	private static int defaultPort = 8888;
	public static ArrayList<Socket> socketList = new ArrayList<Socket>();

//	private OutputStream out = null;

	private TextView tvMovementSpeed;
    private Button btnMoveForward;
	private Button btnMoveForwardLeft;
	private Button btnMoveForwardRight;
	private Button btnMoveDown;
	private Button btnMoveDownLeft;
	private Button btnMoveDownRight;
	private Button btnMoveRight;
	private Button btnMoveLeft;
	private int movementSpeed = 0;
	private int directionState = DirectionState.STOP;
	private Handler handler = null;
	private Socket socket = null;
	String tag = "chatRoom";
	private BufferedReader buRead = null;

	private final int UPDATE_HISTORY_CONTENT = 0;
	private final int UPDATE_INPUT_CONTENT = 1;
	private final int UPDATE_IP_CONTENT = 3;
	private final int MUSIC_PLAYSONG = 4;
	private final int MUSIC_PLAYSTORY = 5;
	private final int MUSIC_PAUSE = 6;

	private final int CAR_LEFT = 7;
	private final int CAR_RIGHT = 8;
	private final int CAR_FORWARD = 9;
	private final int CAR_BACK = 10;

	private WifiStateReceiver wifiStateReceiver;
	ServerSocket ss;
	WifiManager wifiManager;
	WifiInfo wifiInfo;
	MyThread t;
	InetAddress address;

	String local_ip, dest_ip=null;

	private volatile boolean isBroadcastRuning = true, serverrun = true, mBound = false;

	private MulticastSocket ms;
	MulticastLock multicastLock;
	DatagramPacket dataPacket = null;

	Thread clientThread, serverThread;

	private MusicService audioService;
	Intent serviceIntent;

	private CameraManager cameraManager;
	private OrientationManager orientationManager;
	private SurfaceView surfacePreview;

	private int selectedPreviewSize=1,imageQuality=80;// 0-100
	private RelativeLayout layoutParent;

	private boolean isConnected = false;
	private OutputStream outputStream;
	private DataOutputStream dos;

	private ConnectionManager connectionManager;

	private String password="1234";
	private int lastPictureTakenTime = 0;
	private static final int TAKE_PICTURE_COOLDOWN = 1000;

	// 使用ServiceConnection来监听Service状态的变化
	private ServiceConnection conn = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
			audioService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			// 这里我们实例化audioService,通过binder来实现
			MyBinder myBinder = (MyBinder) binder;
			// 获取service
			audioService = (MusicService) myBinder.getService();
			mBound = true;
//			button_play.setEnabled(true);
//			button_pause.setEnabled(true);

		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);//去掉标题栏
		setContentView(R.layout.activity_main_show);

		init();

		surfacePreview = (SurfaceView) findViewById(R.id.surface_preview);
		surfacePreview.getHolder().addCallback(this);
		surfacePreview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		orientationManager = new OrientationManager(this);
		cameraManager = new CameraManager(selectedPreviewSize);
		cameraManager.setCameraManagerListener(this);

		layoutParent = (RelativeLayout) findViewById(R.id.layout_parent);
		layoutParent.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				cameraManager.requestAutoFocus();
			}
		});


		wifiStateReceiver = new WifiStateReceiver();

		wifiStateReceiver.setBRInteractionListener(this);



		// 获取wifi服务
		wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		// 判断wifi是否开启
		if (!wifiManager.isWifiEnabled()) {
			wifiManager.setWifiEnabled(true);
		} else {

			wifiInfo = wifiManager.getConnectionInfo();
			int ipAddress = wifiInfo.getIpAddress();
//			et = (TextView) findViewById(R.id.TextView_ip_localip);
			local_ip = intToIp(ipAddress);
//			et.setText(local_ip);
		}

		// 绑定service;
		serviceIntent = new Intent(this, MusicService.class);

		// 如果未绑定，则进行绑定
		if (!mBound) {
			bindService(serviceIntent, conn, Context.BIND_AUTO_CREATE);
		}
//		tv_ip_view.setText((String) msg.obj);
		initService();
	}

	public void init() {
//		button_send = (Button) findViewById(R.id.button_send);
//		et_ip = (EditText) findViewById(R.id.editText_ip);
//		et_port = (EditText) findViewById(R.id.EditText_port);
//		et_conent = (EditText) findViewById(R.id.EditText_content);
//		tv_history = (TextView) findViewById(R.id.textView_history_content);
//		checkBoxSwitch = (CheckBox) findViewById(R.id.checkBox_server_start);
//
//		button_play = (Button) findViewById(R.id.button_play);
//		button_pause = (Button) findViewById(R.id.button_pause);
//		button_play.setEnabled(false);
//		button_pause.setEnabled(false);
		tvMovementSpeed = (TextView) findViewById(R.id.tv_movement_speed);
		btnMoveForward = (Button) findViewById(R.id.btn_move_forward);
		btnMoveForwardLeft = (Button) findViewById(R.id.btn_move_forward_left);
		btnMoveForwardRight = (Button) findViewById(R.id.btn_move_forward_right);
		btnMoveDown = (Button) findViewById(R.id.btn_move_backward);
		btnMoveDownLeft = (Button) findViewById(R.id.btn_move_backward_left);
		btnMoveDownRight = (Button) findViewById(R.id.btn_move_backward_right);
		btnMoveRight = (Button) findViewById(R.id.btn_move_right);
		btnMoveLeft = (Button) findViewById(R.id.btn_move_left);
		// et = (TextView)findViewById(R.id.TextView_ip_localip);
		initCameraPreviewSize();
		tv_ip_view=(TextView)findViewById(R.id.tv_ip_address);
	}

	private void initService(){
		initWifi();
		connectionManager = new ConnectionManager(password);
		connectionManager.start();
		connectionManager.setConnectionListener(this);
		connectionManager.setCommandListener(this);
		connectionManager.setSendCommandListener(this);
		serverrun=false;
		Log.d(tag, "onCreate===================" );
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub

		Log.d(tag, "onStart===================" );
//		configure();


//		serverStart();
		super.onStart();
	}

	@SuppressWarnings("deprecation")
	public void initCameraPreviewSize() {
		Camera mCamera;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
			mCamera = Camera.open();
		} else {
			mCamera = Camera.open(0);
		}
		Camera.Parameters params = mCamera.getParameters();
		List<Camera.Size> previewSize=params.getSupportedPreviewSizes();
		if(previewSize.size()==1){
			selectedPreviewSize=0;
		}else if(previewSize.size()>1){
			if(previewSize.get(0).width<previewSize.get(1).width){
				selectedPreviewSize=1;
			}else{
				selectedPreviewSize=previewSize.size()-2;
			}
		}

		mCamera.release();
	}

	private void initWifi() {

		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		this.registerReceiver(wifiStateReceiver, filter);
	}

	public void serverStart() {
		try {
			ss = new ServerSocket(defaultPort);
//			Log.d(tag, "on serverStart======================================");

			serverThread = new Thread() {
				public void run() {
					while (serverrun) {
						try {
							 Log.d(tag, "on serverStart: ready to accept=============");
							socket = ss.accept();
							socketList.add(socket);
							buRead = new BufferedReader(new InputStreamReader(
									socket.getInputStream(), "utf-8"));

							String receive_content = null;
							while ((receive_content = readFromClient()) != null) {

								if (dest_ip == null) {
									dest_ip = socket.getInetAddress()
											.getCanonicalHostName();
									Message msg = new Message();
									msg.what = UPDATE_IP_CONTENT;
									msg.obj = dest_ip;
									handler.sendMessage(msg);
									isConnected=true;
									isBroadcastRuning=false;
//									socket

									// Log.d(tag,
									// "fist time find dest ip address on serverStart: ready to accept="+dest_ip);
								}
								 Log.d(tag,"1 Car客户端说："+receive_content);
								// isRuning=false;
								Message msg = new Message();
								if(receive_content.equals("musicplaysong")){
									msg.what = MUSIC_PLAYSONG;
								}else if(receive_content.equals("musicplaystory")){
									msg.what = MUSIC_PLAYSTORY;
								}
								else if(receive_content.equals("musicpause")){
									msg.what = MUSIC_PAUSE;
								}else{
									String history_content ="test";
;											//tv_history.getText()
											//.toString();
									history_content +=socket.getInetAddress() + "说:"
											+ receive_content + "\n";


									msg.what = UPDATE_HISTORY_CONTENT;
									msg.obj = history_content;
								}


								handler.sendMessage(msg);

								// for (Socket ss:socketList)
								// {
								// OutputStream out=ss.getOutputStream();
								// out.write(("[服务器已经收到消息]"+"\n").getBytes("utf-8"));
								// }
							}

						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}
				}
			};
			serverThread.start();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private String readFromClient() {
		try {
			return buRead.readLine();
		} catch (Exception e) {
			// 删除此Socket
			socketList.remove(socket);
		}
		return null;
	}

	public void clientStart() {

		if (dest_ip == null) {
			Toast.makeText(getApplicationContext(), "未获取对方地址，请稍后！",
					Toast.LENGTH_SHORT).show();
			return;
		}
		clientThread = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					// String ip = et_ip.getText().toString();
					String port =defaultPort+"";// et_port.getText().toString();

					if (!port.equals("") && port != null) {
						socket = new Socket(dest_ip, defaultPort);
					} else {
						socket = new Socket(dest_ip, Integer.parseInt(port));
					}

					outputStream = socket.getOutputStream();
					Log.d(tag, "clientStart success");

				} catch (IOException e) {
					e.printStackTrace();
					Log.d(tag, "clientStart failed " + e.getMessage());
				}
			}
		});
		clientThread.start();

	}

	public void clientStop() {
		clientThread = null;
		try {
			if (socket != null)
				socket.close();
//			if (out != null)
//				out.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	protected void onStop() {
		Log.i("MainActivity", "onStop===============");
		connectionManager.stop();
		super.onStop();
	}
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		this.unregisterReceiver(wifiStateReceiver);
		// if(wifiStateReceiver!=null){
		//
		// }
		Log.i("MainActivity", "onDestroy===============");
		serverrun = false;
		isConnected = false;
		try {
			if (ss != null)
				ss.close();
			// if(out != null)
			// out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		unbindService(conn);
		stopService(serviceIntent);
		super.onDestroy();

	}

	public void setText(String content) {
		if (content != null) {
			Log.d("APActivityt", "setText=======CONNECTED,CONNECTED");
			wifiInfo = wifiManager.getConnectionInfo();
			local_ip = intToIp(wifiInfo.getIpAddress());
//			et.setText("本地：" + local_ip);
			broadcast(local_ip);
			// textView.setText(content);
		}
	}

	private String intToIp(int i) {

		return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF)
				+ "." + (i >> 24 & 0xFF);
	}

	private void allowMulticast() {
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		multicastLock = wifiManager.createMulticastLock("multicast.test");
		multicastLock.acquire();
	}

	private void broadcast(String ip) {
		// 发送的数据包，局网内的所有地址都可以收到该数据包

		allowMulticast();

		try {
			/* 创建socket实例 */
			ms = new MulticastSocket(8093);
			ms.setTimeToLive(1);
			// ms.setLoopbackMode(true);
			// 将本机的IP（这里可以写动态获取的IP）地址放到数据包里，其实server端接收到数据包后也能获取到发包方的IP的
			address = InetAddress.getByName("224.0.0.1");

			ms.joinGroup(address);

			t = new MyThread();
			t.start();

			 Log.e("APActivityt", "broadcast over");
			// ms.send(dataPacket);
			// Log.e("APActivity---1234-after---setText", "broadcast over");

		} catch (Exception e) {
			e.printStackTrace();
		}
		multicastLock.release();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (surfacePreview == null)
			return;

		cameraManager.stopCameraPreview();
		cameraManager.initCameraParameter();

		setupPreviewLayout();

		cameraManager.setCameraOrientation(orientationManager.getOrientation());
		cameraManager.startCameraPreview(surfacePreview);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		cameraManager.createCameraInstance(holder);
		Log.d("focus","=========================surfaceCreated=");
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		cameraManager.destroyCameraInstance();
	}

	@Override
	public void onPictureTaken(String filename, String path) {
		connectionManager.sendCommand(Command.SNAP);
	}

	@Override
	public void onPreviewTaken(Bitmap bitmap) {
		if (isConnected) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, imageQuality, bos);
			if(connectionManager!=null)
			connectionManager.sendImageData(bos.toByteArray());
		}
	}



	@Override
	public void onPreviewOutOfMemory(OutOfMemoryError e) {
		e.printStackTrace();
		showToast(getString(R.string.out_of_memory));
		finish();
	}
	public void showToast(String message) {
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
	}

	@SuppressWarnings("deprecation")
	public void setupPreviewLayout() {
		Display display = getWindowManager().getDefaultDisplay();
		ViewGroup.LayoutParams lp = layoutParent.getLayoutParams();

		float previewWidth = cameraManager.getPreviewSize().width;
		float previewHeight = cameraManager.getPreviewSize().height;

		int orientation = orientationManager.getOrientation();
		float ratio = 0;
		if (orientation == OrientationManager.LANDSCAPE_NORMAL
				|| orientation == OrientationManager.LANDSCAPE_REVERSE) {
			ratio = previewWidth / previewHeight;
		} else if (orientation == OrientationManager.PORTRAIT_NORMAL
				|| orientation == OrientationManager.PORTRAIT_REVERSE) {
			ratio = previewHeight / previewWidth;
		}
		if ((int) ((float) surfacePreview.getWidth() / ratio) >= display.getHeight()) {
			lp.height = (int) ((float) surfacePreview.getWidth() / ratio);
			lp.width = surfacePreview.getWidth();
		} else {
			lp.height = surfacePreview.getHeight();
			lp.width = (int) ((float) surfacePreview.getHeight() * ratio);
		}

		layoutParent.setLayoutParams(lp);
		int locationX = (int) (lp.width / 2.0 - surfacePreview.getWidth() / 2.0);
		layoutParent.animate().translationX(-locationX);
	}

	@Override
	public void onControllerConnected(Socket socket) {
		isConnected = true;
		InetAddress ip = socket.getInetAddress();
		tv_ip_view.setText("connected");
//			if (dest_ip == null) {
//			dest_ip=ip.getHostName();
//			Message msg = new Message();
//			msg.what = UPDATE_IP_CONTENT;
//			msg.obj = dest_ip;
//			handler.sendMessage(msg);
//		}
		isConnected=true;
//		isBroadcastRuning=false;// 确保一直可以让控制端连接，如果控制端重启也可以
//		tv_ip_view.setText((CharSequence) ip);
		connectionManager.sendCommand(Command.ACCEPT_CONNECTION);
	}
	public void clearCheckBox() {
		btnMoveForward.setPressed(false);
		btnMoveForwardLeft.setPressed(false);
		btnMoveForwardRight.setPressed(false);
		btnMoveDown.setPressed(false);
		btnMoveDownLeft.setPressed(false);
		btnMoveDownRight.setPressed(false);
		btnMoveRight.setPressed(false);
		btnMoveLeft.setPressed(false);
	}

	@Override
	public void onWrongPassword() {
		connectionManager.sendCommand(Command.WRONG_PASSWORD);
		connectionManager.restart();
	}

	@Override
	public void onControllerDisconnected() {
		showToast(getString(R.string.connection_down));
		tv_ip_view.setText("connection break");
	}

	@Override
	public void onControllerClosed() {
		isConnected = false;
		tv_ip_view.setText("connection break");
	}

	@Override
	public void onDataIncoming() {
		clearCheckBox();
	}

	@Override
	public void onFlashCommand(String command) {
		if (cameraManager.isFlashAvailable()) {
			if (command.equals(Command.LED_ON)) {
				cameraManager.requestFlashOn();
			} else if (command.equals(Command.LED_OFF)) {
				cameraManager.requestFlashOff();
			}
		} else {
			connectionManager.sendCommand(Command.FLASH_UNAVAILABLE);
		}
	}

	@Override
	public void onRequestTakePicture() {
		double currentTimeSeconds = System.currentTimeMillis();
		if (currentTimeSeconds - lastPictureTakenTime > TAKE_PICTURE_COOLDOWN) {
			lastPictureTakenTime = (int) currentTimeSeconds;
			cameraManager.requestTakePicture();
		}
	}

	@Override
	public void onRequestAutoFocus() {
		cameraManager.requestAutoFocus();
	}

	@Override
	public void onRequestPlaySong() {
		if (mBound) {
			audioService.playSong();

		}
	}
	@Override
	public void onRequestPlayStory() {
		if (mBound) {
			audioService.playStory();

		}
	}

	@Override
	public void onRequestPlayPause() {
		if (mBound) {
			audioService.pause();

		}
	}



	@Override
	public void onMoveForwardCommand(int movementSpeed) {
		btnMoveForward.setPressed(true);
		directionState = DirectionState.UP;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveForwardRightCommand(int movementSpeed) {
		btnMoveForwardRight.setPressed(true);
		directionState = DirectionState.UPRIGHT;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveForwardLeftCommand(int movementSpeed) {
		btnMoveForwardLeft.setPressed(true);
		directionState = DirectionState.UPLEFT;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveBackwardCommand(int movementSpeed) {
		btnMoveDown.setPressed(true);
		directionState = DirectionState.DOWN;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveBackwardRightCommand(int movementSpeed) {
		btnMoveDownRight.setPressed(true);
		directionState = DirectionState.DOWNRIGHT;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveBackwardLeftCommand(int movementSpeed) {
		btnMoveDownLeft.setPressed(true);
		directionState = DirectionState.DOWNLEFT;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveLeftCommand(int movementSpeed) {
		btnMoveLeft.setPressed(true);
		directionState = DirectionState.LEFT;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveRightCommand(int movementSpeed) {
		btnMoveRight.setPressed(true);
		directionState = DirectionState.RIGHT;
		updateMovementSpeed(movementSpeed);
	}

	@Override
	public void onMoveStopCommand() {
		directionState = DirectionState.STOP;
		updateMovementSpeed(0);
	}

	@Override
	public void onSendCommandSuccess() {

	}

	@Override
	public void onSendCommandFailure() {
		isConnected = false;
	}

	public void updateMovementSpeed(int speed) {
		movementSpeed = speed;
		tvMovementSpeed.setText(getString(R.string.movement_speed, speed));
	}

	class MyThread extends Thread {

		@Override
		public void run() {

			// ip=""+k;
			byte[] data = local_ip.getBytes();
			// 224.0.0.1为广播地址

			// 这个地方可以输出判断该地址是不是广播类型的地址
			// Log.e("APActivity---1234----setTextaddress.isMulticastAddress()=",
			// address.isMulticastAddress() + "");
			while (isBroadcastRuning) {
				// if(isRuning)
				// {
				// ip=""+k;
				data = local_ip.getBytes();
				dataPacket = new DatagramPacket(data, data.length, address,
						8093);
				// k++;
				try {
					// Log.e("APActivity---1234-before---setText",
					// "broadcast over");
					ms.send(dataPacket);
					Thread.sleep(3000);
					// Log.e("APActivity---1234-after---setText",
					// "broadcast over");
					// System.out.println("再次发送ip地址广播:.....");
					// isRuning = false;

					 Log.e("APActivity", "broadcast over000000");

				} catch (Exception e) {
					e.printStackTrace();
				}
				// }
			}
			try {

				ms.leaveGroup(address);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}// 自己不接收广播
			ms.close();

		}

	}
	private static Boolean isQuit = false;
	private Timer timer = new Timer();
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (!isQuit) {
				isQuit = true;
				Toast.makeText(getBaseContext(),
						R.string.back_more_quit,Toast.LENGTH_LONG).show();
				TimerTask task = null;
				task = new TimerTask() {
					@Override
					public void run() {
						isQuit = false;
					}
				};
				timer.schedule(task, 2000);
			} else {
				finish();
				System.exit(0);
			}
		}
		return false;
	}


}