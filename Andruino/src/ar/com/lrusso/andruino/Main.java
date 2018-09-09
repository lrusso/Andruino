package ar.com.lrusso.andruino;

import ar.com.lrusso.andruino.FTDriver;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;

public class Main extends Activity
	{
	private static final boolean USE_WRITE_BUTTON_FOR_DEBUG = false;
	private static final int TEXT_MAX_SIZE = 8192;
	private static final int REQUEST_PREFERENCE         = 0;
	private static final int REQUEST_WORD_LIST_ACTIVITY = 1;
	private static final int DISP_CHAR  = 0;
	private static final int DISP_DEC   = 1;
	private static final int DISP_HEX   = 2;
	private static final int LINEFEED_CODE_CR   = 0;
	private static final int LINEFEED_CODE_CRLF = 1;
	private static final int LINEFEED_CODE_LF   = 2;
	private static final String BUNDLEKEY_LOADTEXTVIEW = "bundlekey.LoadTextView";
	
	private Menu mainMenu;	

	FTDriver mSerial;
	private ScrollView mSvText;
	private EditText mTvSerial;
	private StringBuilder mText = new StringBuilder();
	private boolean mStop = false;
	boolean lastDataIs0x0D = false;
	Handler mHandler = new Handler();
	private Button btWrite;
	private EditText etWrite;
	private int mDisplayType = DISP_CHAR;
	private int mReadLinefeedCode = LINEFEED_CODE_LF;
	private int mWriteLinefeedCode = LINEFEED_CODE_LF;
	private int mBaudrate = FTDriver.BAUD9600;
	private int mDataBits = FTDriver.FTDI_SET_DATA_BITS_8;
	private int mParity = FTDriver.FTDI_SET_DATA_PARITY_NONE;
	private int mStopBits = FTDriver.FTDI_SET_DATA_STOP_BITS_1;
	private int mFlowControl = FTDriver.FTDI_SET_FLOW_CTRL_NONE;
	private int mBreak = FTDriver.FTDI_SET_NOBREAK;
	private boolean mRunningMainLoop = false;
	private static final String ACTION_USB_PERMISSION = "ar.com.lrusso.andruino.USB_PERMISSION";
	private final static String BR = System.getProperty("line.separator");
	private static boolean connected = false;

	@Override public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		mSvText = (ScrollView) findViewById(R.id.receiverScrollbar);
		mTvSerial = (EditText) findViewById(R.id.receiverTextbox);
		btWrite = (Button) findViewById(R.id.senderButton);
		btWrite.setTextColor(Color.LTGRAY);
		btWrite.setEnabled(false);
		etWrite = (EditText) findViewById(R.id.senderTextbox);
		etWrite.setEnabled(false);
		btWrite.setText(R.string.textSend);
		verificarBaudRate();
		mSerial = new FTDriver((UsbManager) getSystemService(Context.USB_SERVICE));
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		mBaudrate = loadDefaultBaudrate();
		PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		mSerial.setPermissionIntent(permissionIntent);
		if (mSerial.begin(mBaudrate))
			{
			loadDefaultSettingValues();
			mainloop();
			}
			else
			{
			messageStatus(getResources().getString(R.string.textNoConnection));
			}
		etWrite.setOnKeyListener(new OnKeyListener()
			{
			@Override public boolean onKey(View v, int keyCode, KeyEvent event)
				{
				if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER)
					{
					writeDataToSerial();
					return true;
					}
				return false;
				}});
		if (!USE_WRITE_BUTTON_FOR_DEBUG)
			{
			btWrite.setOnClickListener(new View.OnClickListener()
				{
				@Override public void onClick(View v)
					{
					writeDataToSerial();
					}
				});
			}
			else
			{
			btWrite.setOnClickListener(new View.OnClickListener()
				{
				@Override public void onClick(View v)
					{
					String strWrite = "";
					for (int i = 0; i < 3000; ++i)
						{
						strWrite = strWrite + " " + Integer.toString(i);
						}
					mSerial.write(strWrite.getBytes(), strWrite.length());
					}
				});
			}
		}
	
	@Override public void onDestroy()
		{
		mSerial.end();
		mStop = true;
		unregisterReceiver(mUsbReceiver);
		super.onDestroy();
		}
	
	@Override public boolean onCreateOptionsMenu(Menu menu)
		{
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_activity_actions, menu);
	    mainMenu=menu;
		return super.onCreateOptionsMenu(menu);
		}
	
	@Override public boolean onOptionsItemSelected(MenuItem item)
		{
	    switch (item.getItemId())
    		{
    		case R.id.action_settings:
    		View menuItemView = findViewById(R.id.action_settings);
    		PopupMenu popupMenu = new PopupMenu(this, menuItemView); 
    		popupMenu.inflate(R.menu.popup_menu);
    		
    		Menu popupMenu2 = popupMenu.getMenu();

    		if (connected==true)
    			{
        	    popupMenu2.findItem(R.id.conectarse).setVisible(false);
    			}
    			else
    			{
    			popupMenu2.findItem(R.id.desconectarse).setVisible(false);
    			}
    		
    		popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
        		{  
    			public boolean onMenuItemClick(MenuItem item)
            		{
    				if (item.getTitle().toString().contains(getResources().getString(R.string.textConnect)))
    					{
    					openUsbSerial();
    					}
    				else if (item.getTitle().toString().contains(getResources().getString(R.string.textDisconnect)))
    					{
    					closeUsbSerial();
    					}
    				else if (item.getTitle().toString().contains(getResources().getString(R.string.textBaudRate)))
    					{
    					clickInBaudRate();
    					}
					else if (item.getTitle().toString().contains(getResources().getString(R.string.textSketch)))
						{
						clickInSketch();
						}
	    			else if (item.getTitle().toString().contains(getResources().getString(R.string.textPrivacy)))
						{
	    				clickInPrivacy();
						}
    				else if (item.getTitle().toString().contains(getResources().getString(R.string.textAbout)))
    					{
    					clickInAbout();
    					}
    				return true;  
            		}  
        		});              
    		popupMenu.show();
    		return true;
    		
    		default:
    		return super.onOptionsItemSelected(item);
    		}
		}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data)
		{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_WORD_LIST_ACTIVITY)
			{
			if(resultCode == RESULT_OK)
				{
				try
					{
					String strWord = data.getStringExtra("word");
					etWrite.setText(strWord);
					etWrite.setSelection(etWrite.getText().length());
					}
					catch(Exception e)
					{
					}
				}
			}
			else if (requestCode == REQUEST_PREFERENCE)
				{
				SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
				String res = pref.getString("display_list", Integer.toString(DISP_CHAR));
				mDisplayType = Integer.valueOf(res);
				res = pref.getString("fontsize_list", Integer.toString(12));
				res = pref.getString("readlinefeedcode_list", Integer.toString(LINEFEED_CODE_CRLF));
				mReadLinefeedCode = Integer.valueOf(res);
				res = pref.getString("databits_list", Integer.toString(FTDriver.FTDI_SET_DATA_BITS_8));
				if (mDataBits != Integer.valueOf(res))
					{
					mDataBits = Integer.valueOf(res);
					mSerial.setSerialPropertyDataBit(mDataBits, FTDriver.CH_A);
					mSerial.setSerialPropertyToChip(FTDriver.CH_A);
					}
				int intRes;
				res = pref.getString("parity_list",Integer.toString(FTDriver.FTDI_SET_DATA_PARITY_NONE));
				intRes = Integer.valueOf(res) << 8;
				if (mParity != intRes)
					{
					mParity = intRes;
					mSerial.setSerialPropertyParity(mParity, FTDriver.CH_A);
					mSerial.setSerialPropertyToChip(FTDriver.CH_A);
					}
				res = pref.getString("stopbits_list",Integer.toString(FTDriver.FTDI_SET_DATA_STOP_BITS_1));
				intRes = Integer.valueOf(res) << 11;
				if (mStopBits != intRes)
					{
					mStopBits = intRes;
					mSerial.setSerialPropertyStopBits(mStopBits, FTDriver.CH_A);
					mSerial.setSerialPropertyToChip(FTDriver.CH_A);
					}
				res = pref.getString("flowcontrol_list",Integer.toString(FTDriver.FTDI_SET_FLOW_CTRL_NONE));
				intRes = Integer.valueOf(res) << 8;
				if (mFlowControl != intRes)
					{
					mFlowControl = intRes;
					mSerial.setFlowControl(FTDriver.CH_A, mFlowControl);
					}
				res = pref.getString("break_list", Integer.toString(FTDriver.FTDI_SET_NOBREAK));
				intRes = Integer.valueOf(res) << 14;
				if (mBreak != intRes)
					{
					mBreak = intRes;
					mSerial.setSerialPropertyBreak(mBreak, FTDriver.CH_A);
					mSerial.setSerialPropertyToChip(FTDriver.CH_A);
					}
				res = pref.getString("baudrate_list", Integer.toString(FTDriver.BAUD9600));
				if (mBaudrate != Integer.valueOf(res))
					{
					mBaudrate = Integer.valueOf(res);
					mSerial.setBaudrate(mBaudrate, 0);
					}
				}
		}
	
	@Override protected void onSaveInstanceState(Bundle outState)
		{
		super.onSaveInstanceState(outState);
		outState.putString(BUNDLEKEY_LOADTEXTVIEW, mTvSerial.getText().toString());
		}
	
	@Override protected void onRestoreInstanceState(Bundle savedInstanceState)
		{
		super.onRestoreInstanceState(savedInstanceState);
		mTvSerial.setText(savedInstanceState.getString(BUNDLEKEY_LOADTEXTVIEW));
		}
	
	@Override public boolean onKeyUp(int keyCode, KeyEvent event)
		{
		try
			{
			if (keyCode == KeyEvent.KEYCODE_MENU)
				{
				if (mainMenu!=null)
            		{
					mainMenu.performIdentifierAction(R.id.action_settings, 0);
            		}				
				}
			}
			catch(NullPointerException e)
			{
			}
		return super.onKeyUp(keyCode, event);
		}
	
	private void writeDataToSerial()
		{
		String strWrite = etWrite.getText().toString();
		strWrite = changeLinefeedcode(strWrite);
		mSerial.write(strWrite.getBytes(), strWrite.length()-1);
		etWrite.setText("");
		}

    public void verificarBaudRate()
		{
    	String value = readFile("baudrate.cfg");
    	if (value=="_")
    		{
    		writeFile("baudrate.cfg","9600");
    		mBaudrate = FTDriver.BAUD9600;
    		}
    	if (value.contains("9600"))
    		{
        	mBaudrate = FTDriver.BAUD9600;
    		}
    		else
    		{
    		if (value.contains("57600"))
    			{
            	mBaudrate = FTDriver.BAUD57600;
    			}
    			else
    			{
   	    		if (value.contains("115200"))
        			{
   	            	mBaudrate = FTDriver.BAUD115200;
        			}
   	    			else
   	    			{
 	    	        mBaudrate = FTDriver.BAUD9600;
   	    			}
    			}
    		}
		}
    
    public String readFile(String archivo)
    	{
    	String value;
    	value = "_";
    	try
    		{
    		FileInputStream fis = openFileInput(archivo);
    		InputStreamReader in = new InputStreamReader(fis);
    		BufferedReader br = new BufferedReader(in);
    		value=br.readLine();
    		br.close();
    		}
			catch (IOException e)
			{
			}
    	return value;
    	}

    public void writeFile(String file, String value)
    	{
    	try
    		{
    		String FILENAME = file;
    		String string = value;
    		FileOutputStream fos = openFileOutput(FILENAME, Context.MODE_PRIVATE);
    		fos.write(string.getBytes());
    		fos.close();
    		}
			catch(IOException c)
			{
			}
    	}
	
	private String changeLinefeedcode(String str)
		{
		str = str.replace("\\r", "\r");
		str = str.replace("\\n", "\n");
		switch (mWriteLinefeedCode)
			{
			case LINEFEED_CODE_LF:
			str = str + "\n";
			break;
			
			default:
			}
		return str;
		}
	
	public void setWriteTextString(String str)
		{
		etWrite.setText(str);
		}

	public void clickInBaudRate()
		{
		if (connected==true)
			{
			message(getResources().getString(R.string.textDisconnectBoard),getResources().getString(R.string.textMessage),getResources().getString(R.string.textOk));
			}
			else
			{
	    	final String speed9600 = "9600";
	    	final String speed57600 = "57600";
	    	final String speed115200 = "115200";
	    	final AlertDialog.Builder singlechoicedialog = new AlertDialog.Builder(this);
	    	final CharSequence[] Report_items = {speed9600, speed57600, speed115200};
	    	singlechoicedialog.setTitle(getResources().getString(R.string.textBaudRate));
	    	int seleccionbaudrate=0;

	    	String value = readFile("baudrate.cfg");
	    	if (value=="_")
	    		{
	    		writeFile("baudrate.cfg","9600");
	    		mBaudrate = FTDriver.BAUD9600;
	    		seleccionbaudrate=0;
	    		}
    		
	    	if (value.contains("9600"))
	    		{
	        	mBaudrate = FTDriver.BAUD9600;
	    		seleccionbaudrate=0;
	    		}
	    		else
	    		{
	    		if (value.contains("57600"))
	    			{
		    		seleccionbaudrate=1;
	            	mBaudrate = FTDriver.BAUD57600;
	    			}
	    			else
	    			{
	   	    		if (value.contains("115200"))
	        			{
			    		seleccionbaudrate=2;
	   	            	mBaudrate = FTDriver.BAUD115200;
	        			}
	   	    			else
	   	    			{
				    	seleccionbaudrate=0;
	 	    	        mBaudrate = FTDriver.BAUD9600;
	   	    			}
	    			}
	    		}

	    	singlechoicedialog.setSingleChoiceItems(Report_items, seleccionbaudrate,new DialogInterface.OnClickListener()
	    		{
	    		public void onClick(DialogInterface dialog, int item)
	    			{
	    			String value = Report_items[item].toString();
	    			if (value==speed9600)
	    				{
	    	    		writeFile("baudrate.cfg","9600");
	    	    		mBaudrate = FTDriver.BAUD9600;
	    				}
	    			if (value==speed57600)
	    				{
	    	    		writeFile("baudrate.cfg","57600");
	    	    		mBaudrate = FTDriver.BAUD57600;
	    				}
	    			if (value==speed115200)
	    				{
	    	    		writeFile("baudrate.cfg","115200");
	    	    		mBaudrate = FTDriver.BAUD115200;
	    				}
	    			dialog.cancel();
	    			}
	    		});
	    	AlertDialog alert_dialog = singlechoicedialog.create();
	    	alert_dialog.show();
			}
		}
	
	private void clickInSketch()
		{
		LayoutInflater inflater = LayoutInflater.from(this);
		View view=inflater.inflate(R.layout.sketch, null);

		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);  
		alertDialog.setTitle(getResources().getString(R.string.textSketch));  
		alertDialog.setView(view);
		alertDialog.setPositiveButton(getResources().getString(R.string.textOk), new DialogInterface.OnClickListener()
			{
			public void onClick(DialogInterface dialog, int whichButton)
				{
				}
			});
		alertDialog.show();
		}

	public void clickInPrivacy()
		{
		LayoutInflater inflater = LayoutInflater.from(this);
		View view=inflater.inflate(R.layout.privacy, null);

		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);  
		alertDialog.setTitle(getResources().getString(R.string.textPrivacy));  
		alertDialog.setView(view);
		alertDialog.setPositiveButton(getResources().getString(R.string.textOk), new DialogInterface.OnClickListener()
			{
			public void onClick(DialogInterface dialog, int whichButton)
				{
				}
			});
		alertDialog.show();
		}
	
	public void clickInAbout()
		{
		String anos = "";
		String value = getResources().getString(R.string.textAboutMessage);
		int lastTwoDigits = Calendar.getInstance().get(Calendar.YEAR) % 100;
		if (lastTwoDigits<=5)
			{
			anos = "2005";
			}
			else
			{
			anos ="2005 - 20" + String.valueOf(lastTwoDigits).trim();
			}
		
		value = value.replace("ANOS", anos);
		
		TextView msg = new TextView(this);
		msg.setText(Html.fromHtml(value));
		msg.setPadding(10, 20, 10, 25);
		msg.setGravity(Gravity.CENTER);
		float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
		float size = new EditText(this).getTextSize() / scaledDensity;
		msg.setTextSize(size);			

		new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.textAbout)).setView(msg).setIcon(R.drawable.ic_launcher).setPositiveButton(getResources().getString(R.string.textOk),new DialogInterface.OnClickListener()
			{
			public void onClick(DialogInterface dialog,int which)
				{
				}
			}).show();
		}
	
	public void message(String a, String b, String c)
		{
		new AlertDialog.Builder(this).setTitle(b).setMessage(a).setPositiveButton(c,new DialogInterface.OnClickListener()
			{
			public void onClick(DialogInterface dialog,int which)
				{
				}
			}).show();
		}
	
	private void mainloop()
		{
		mStop = false;
		mRunningMainLoop = true;
		btWrite.setTextColor(Color.BLACK);
		btWrite.setEnabled(true);
		etWrite.setText("");
		etWrite.setEnabled(true);
		connected=true;
		messageStatus(getResources().getString(R.string.textConnected));
		new Thread(mLoop).start();
		}
	
	private Runnable mLoop = new Runnable()
		{
		@Override public void run()
			{
			int len;
			byte[] rbuf = new byte[4096];
			for (;;)
				{
				len = mSerial.read(rbuf);
				rbuf[len] = 0;
				if (len > 0)
					{
					switch (mDisplayType)
						{
						case DISP_CHAR:
						setSerialDataToTextView(mDisplayType, rbuf, len, "", "");
						break;
						
						case DISP_DEC:
						setSerialDataToTextView(mDisplayType, rbuf, len, "013", "010");
						break;
						
						case DISP_HEX:
						setSerialDataToTextView(mDisplayType, rbuf, len, "0d", "0a");
						break;
						}
					mHandler.post(new Runnable()
						{
						public void run()
							{
							if (mTvSerial.length() > TEXT_MAX_SIZE)
								{
								StringBuilder sb = new StringBuilder();
								sb.append(mTvSerial.getText());
								sb.delete(0, TEXT_MAX_SIZE / 2);
								mTvSerial.setText(sb);
								}
							mTvSerial.append(mText);
							mText.setLength(0);
							mSvText.fullScroll(ScrollView.FOCUS_DOWN);
							}
						});
					}
				try
					{
					Thread.sleep(50);
					}
					catch (InterruptedException e)
					{
					}
				if (mStop)
					{
					mRunningMainLoop = false;
					return;
					}
				}
			}
		};
	
	private String IntToHex2(int Value)
		{
		char HEX2[] = {Character.forDigit((Value >> 4) & 0x0F, 16),Character.forDigit(Value & 0x0F, 16)};
		String Hex2Str = new String(HEX2);
		return Hex2Str;
		}
	
	void setSerialDataToTextView(int disp, byte[] rbuf, int len, String sCr, String sLf)
		{
		int tmpbuf;
		for (int i = 0; i < len; ++i)
			{
			if ((mReadLinefeedCode == LINEFEED_CODE_CR) && (rbuf[i] == 0x0D))
				{
				mText.append(sCr);
				mText.append(BR);
				}
			else if ((mReadLinefeedCode == LINEFEED_CODE_LF) && (rbuf[i] == 0x0A))
				{
				mText.append(sLf);
				mText.append(BR);
				}
			else if ((mReadLinefeedCode == LINEFEED_CODE_CRLF) && (rbuf[i] == 0x0D) && (rbuf[i + 1] == 0x0A))
				{
				mText.append(sCr);
				if (disp != DISP_CHAR)
					{
					mText.append(" ");
					}
				mText.append(sLf);
				mText.append(BR);
				++i;
				}
			else if ((mReadLinefeedCode == LINEFEED_CODE_CRLF) && (rbuf[i] == 0x0D))
				{
				mText.append(sCr);
				lastDataIs0x0D = true;
				}
			else if (lastDataIs0x0D && (rbuf[0] == 0x0A))
				{
				if (disp != DISP_CHAR)
					{
					mText.append(" ");
					}
				mText.append(sLf);
				mText.append(BR);
				lastDataIs0x0D = false;
				}
			else if (lastDataIs0x0D && (i != 0))
				{
				lastDataIs0x0D = false;
				--i;
				}
			else
				{
				switch (disp)
					{
					case DISP_CHAR:
					mText.append((char) rbuf[i]);
					break;

					case DISP_DEC:
					tmpbuf = rbuf[i];
					if (tmpbuf < 0)
						{
						tmpbuf += 256;
						}
					mText.append(String.format("%1$03d", tmpbuf));
					mText.append(" ");
					break;

					case DISP_HEX:
					mText.append(IntToHex2((int) rbuf[i]));
					mText.append(" ");
					break;

					default:
					break;
					}
				}
			}
		}
	
	void loadDefaultSettingValues()
		{
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		String res = pref.getString("display_list", Integer.toString(DISP_CHAR));
		mDisplayType = Integer.valueOf(res);
		res = pref.getString("fontsize_list", Integer.toString(12));
		res = pref.getString("readlinefeedcode_list", Integer.toString(LINEFEED_CODE_CRLF));
		mReadLinefeedCode = Integer.valueOf(res);
		res = pref.getString("databits_list", Integer.toString(FTDriver.FTDI_SET_DATA_BITS_8));
		mDataBits = Integer.valueOf(res);
		mSerial.setSerialPropertyDataBit(mDataBits, FTDriver.CH_A);
		res = pref.getString("parity_list", Integer.toString(FTDriver.FTDI_SET_DATA_PARITY_NONE));
		mParity = Integer.valueOf(res) << 8;
		mSerial.setSerialPropertyParity(mParity, FTDriver.CH_A);
		res = pref.getString("stopbits_list", Integer.toString(FTDriver.FTDI_SET_DATA_STOP_BITS_1));
		mStopBits = Integer.valueOf(res) << 11;
		mSerial.setSerialPropertyStopBits(mStopBits, FTDriver.CH_A);
		res = pref.getString("flowcontrol_list", Integer.toString(FTDriver.FTDI_SET_FLOW_CTRL_NONE));
		mFlowControl = Integer.valueOf(res) << 8;
		mSerial.setFlowControl(FTDriver.CH_A, mFlowControl);
		res = pref.getString("break_list", Integer.toString(FTDriver.FTDI_SET_NOBREAK));
		mBreak = Integer.valueOf(res) << 14;
		mSerial.setSerialPropertyBreak(mBreak, FTDriver.CH_A);
		mSerial.setSerialPropertyToChip(FTDriver.CH_A);
		}
	
	int loadDefaultBaudrate()
		{
		int res = FTDriver.BAUD9600;
    	String value = readFile("baudrate.cfg");
    	if (value=="_")
    		{
    		writeFile("baudrate.cfg","9600");
    		mBaudrate = FTDriver.BAUD9600;
    		res = FTDriver.BAUD9600;
    		}
    	if (value.contains("9600"))
    		{
        	mBaudrate = FTDriver.BAUD9600;
    		res = FTDriver.BAUD9600;
    		}
    		else
    		{
    		if (value.contains("57600"))
    			{
            	mBaudrate = FTDriver.BAUD57600;
        		res = FTDriver.BAUD57600;
    			}
    			else
    			{
   	    		if (value.contains("115200"))
        			{
   	            	mBaudrate = FTDriver.BAUD115200;
   	            	res =  FTDriver.BAUD115200;
        			}
   	    			else
   	    			{
 	    	        mBaudrate = FTDriver.BAUD9600;
 	    	        res = FTDriver.BAUD9600;
   	    			}
    			}
    		}
		return res;
		}
	
	private void openUsbSerial()
		{
		if (!mSerial.isConnected())
			{
			mBaudrate = loadDefaultBaudrate();
			if (!mSerial.begin(mBaudrate))
				{
				messageStatus(getResources().getString(R.string.textCantConnect));
				return;
				}
				else
				{
				messageStatus(getResources().getString(R.string.textConnected));
				}
			}
		if (!mRunningMainLoop)
			{
			mainloop();
			}
		}
	
	private void closeUsbSerial()
		{
		detachedUi();
		mStop = true;
		mSerial.end();
		}
	
	protected void onNewIntent(Intent intent)
		{
		openUsbSerial();
		};
		
	private void detachedUi()
		{
		btWrite.setTextColor(Color.LTGRAY);
		btWrite.setEnabled(false);
		etWrite.setText("");
		etWrite.setEnabled(false);
		connected=false;
		messageStatus(getResources().getString(R.string.textDisconnected));
		}
	
	public void messageStatus(String a)
		{
		Toast.makeText(this, a, Toast.LENGTH_SHORT).show();
		}
	
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
		{
		public void onReceive(Context context, Intent intent)
			{
			String action = intent.getAction();
			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
				{
				if (!mSerial.isConnected())
					{
					mBaudrate = loadDefaultBaudrate();
					mSerial.begin(mBaudrate);
					loadDefaultSettingValues();
					}
				if (!mRunningMainLoop)
					{
					mainloop();
					}
				}
				else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
					{
					mStop = true;
					detachedUi();
					mSerial.usbDetached(intent);
					mSerial.end();
					}
				else if (ACTION_USB_PERMISSION.equals(action))
					{
					synchronized (this)
						{
						if (!mSerial.isConnected())
							{
							mBaudrate = loadDefaultBaudrate();
							mSerial.begin(mBaudrate);
							loadDefaultSettingValues();
							}
						}
					if (!mRunningMainLoop)
						{
						mainloop();
						}
					}
			}
		};
	}