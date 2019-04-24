package ar.com.lrusso.andruino;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Html;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends Activity
	{
    private AppService usbService;
    private MyHandler mHandler;

	private Context 						context;
	private Activity						activity;

	private Button							senderButton;
	private EditText						senderTextbox;

	public static EditText					receiverTextbox;
	public static ScrollView 				receiverScrollbar;
	
	private boolean connected = false;

	@Override public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		senderButton = (Button) findViewById(R.id.senderButton);
		senderTextbox = (EditText) findViewById(R.id.senderTextbox);

		receiverTextbox = (EditText) findViewById(R.id.receiverTextbox);
		receiverScrollbar = (ScrollView) findViewById(R.id.receiverScrollbar);

		context = this;
		activity = this;

		senderButton.setTextColor(Color.LTGRAY);
		senderButton.setEnabled(false);
		senderTextbox.setText("");
		senderTextbox.setEnabled(false);

		setTitle(getResources().getString(R.string.app_name) + " - " + getResources().getString(R.string.textDisconnected));

		senderButton.setOnClickListener(new OnClickListener()
	 		{
			public void onClick(View v)
	    		{
				try
					{
					if (senderTextbox.length()>0)
						{	
						String message = senderTextbox.getText().toString();
					    if (!message.isEmpty())
					    	{
					    	sendMessage(message);
					    	senderTextbox.setText("");
					    	}
						}
					}
					catch(Exception e)
					{
					}
	    		}
	 		});
		
		senderTextbox.setOnEditorActionListener(new EditText.OnEditorActionListener()
			{
			@Override public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2)
				{
		        if (arg1 == EditorInfo.IME_ACTION_SEND)
		        	{
					try
						{
						if (senderTextbox.length()>0)
							{	
							String message = senderTextbox.getText().toString();
							if (!message.isEmpty())
					    		{
			                    sendMessage(message);
								senderTextbox.setText("");
					    		}
							}
						}
						catch(Exception e)
						{
						}
		            return true;
		        	}
		        return false;
				}
			});
				
        mHandler = new MyHandler(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(AppService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(AppService.ACTION_NO_USB);
        filter.addAction(AppService.ACTION_USB_DISCONNECTED);
        filter.addAction(AppService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(AppService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);

        startService(AppService.class, usbConnection);
		}
	
    @Override public void onDestroy()
    	{
    	if(connected==true)
    		{
    		clickInDisconnect();
    		}
    	unregisterReceiver(mUsbReceiver);
    	unbindService(usbConnection);

    	super.onDestroy();
    	}
	
	@Override public boolean onCreateOptionsMenu(Menu menu)
		{
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_activity_actions, menu);
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
        	    popupMenu2.findItem(R.id.connect).setVisible(false);
    			}
    			else
    			{
    			popupMenu2.findItem(R.id.disconnect).setVisible(false);
    			}
    		
    		popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
        		{  
    			public boolean onMenuItemClick(MenuItem item)
            		{
    				if (item.getTitle().toString().contains(getResources().getString(R.string.textConnect)))
						{
    					clickInConnect();
						}
    				else if (item.getTitle().toString().contains(getResources().getString(R.string.textDisconnect)))
    					{
    					clickInDisconnect();
    					}
    				else if (item.getTitle().toString().contains(getResources().getString(R.string.textBaudRate)))
    					{
    					clickInBaudRate();
    					}
					else if (item.getTitle().toString().contains(getResources().getString(R.string.textClear)))
						{
						clickInClearText();
						}
					else if (item.getTitle().toString().contains(getResources().getString(R.string.textCopy)))
						{
						clickInCopyText();
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

	private void clickInConnect()
		{
		try
			{
			usbService.setHandler(mHandler);
			}
			catch(Exception e)
			{
			}
		try
			{
			Toast.makeText(activity, R.string.textConnectedToast, Toast.LENGTH_SHORT).show();
			setTitle(context.getResources().getString(R.string.app_name) + " - " + context.getResources().getString(R.string.textConnected));
			senderButton.setTextColor(Color.BLACK);
			senderButton.setEnabled(true);
			senderTextbox.setText("");
			senderTextbox.setEnabled(true);
			senderTextbox.requestFocus();
			}
			catch(Exception e)
			{
			}
		try
			{
			InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
			}
			catch(Exception e)
			{
			}
		connected = true;
		}

	private void clickInDisconnect()
		{
		try
			{
			usbService.setHandler(null);
			}
			catch(Exception e)
			{
			}
		try
			{
			Toast.makeText(context, context.getResources().getString(R.string.textDisconnectedToast), Toast.LENGTH_LONG).show();
			setTitle(context.getResources().getString(R.string.app_name) + " - " + context.getResources().getString(R.string.textDisconnected));
			senderButton.setTextColor(Color.LTGRAY);
			senderButton.setEnabled(false);
			senderTextbox.setText("");
			senderTextbox.setEnabled(false);
			}
			catch(Exception e)
			{
			}
		try
			{
			InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
			View view = activity.getCurrentFocus();
			if (view == null)
				{
				view = new View(activity);
				}
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);			
			}
			catch(Exception e)
			{
			}
		connected = false;
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
	    	int selectedbaudrate=0;

	    	String value = readFile("baudrate.cfg");
	    	if (value=="_")
	    		{
	    		writeFile("baudrate.cfg","9600");
	    		selectedbaudrate=0;
	    		}
    		
	    	if (value.contains("9600"))
	    		{
	    		selectedbaudrate=0;
	    		}
	    		else
	    		{
	    		if (value.contains("57600"))
	    			{
		    		selectedbaudrate=1;
	    			}
	    			else
	    			{
	   	    		if (value.contains("115200"))
	        			{
			    		selectedbaudrate=2;
	        			}
	   	    			else
	   	    			{
				    	selectedbaudrate=0;
	   	    			}
	    			}
	    		}

	    	singlechoicedialog.setSingleChoiceItems(Report_items, selectedbaudrate,new DialogInterface.OnClickListener()
	    		{
	    		public void onClick(DialogInterface dialog, int item)
	    			{
	    			String value = Report_items[item].toString();
	    			if (value==speed9600)
	    				{
	    	    		writeFile("baudrate.cfg","9600");
	    				}
	    			if (value==speed57600)
	    				{
	    	    		writeFile("baudrate.cfg","57600");
	    				}
	    			if (value==speed115200)
	    				{
	    	    		writeFile("baudrate.cfg","115200");
	    				}
	    			
	    	        Intent intent = new Intent(usbService.ACTION_SERIAL_CONFIG_CHANGED);
	    	        sendBroadcast(intent);
	    	        
	    			dialog.cancel();
	    			}
	    		});
	    	AlertDialog alert_dialog = singlechoicedialog.create();
	    	alert_dialog.show();
			}
		}
	
	private void clickInClearText()
		{
		try
			{
			receiverTextbox.setText("");
			}
			catch(Exception e)
			{
			}
		}

	private void clickInCopyText()
		{
		try
			{
			if (receiverTextbox.getText().toString().length()>0)
				{
				ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); 
				ClipData clip = ClipData.newPlainText("Data",receiverTextbox.getText().toString());
				clipboard.setPrimaryClip(clip);
				Toast.makeText(this,getResources().getString(R.string.textCopyOK),Toast.LENGTH_SHORT).show();
				}
				else
				{
				Toast.makeText(this,getResources().getString(R.string.textCopyError),Toast.LENGTH_SHORT).show();
				}
			}
			catch(Exception e)
			{
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
		String value = getResources().getString(R.string.textAboutMessage);
		value = value.replace("APPNAME",getResources().getString(R.string.app_name));
		
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
	
	
	
	
	
	
	

	private void startService(Class<?> service, ServiceConnection serviceConnection)
		{
		if (!AppService.SERVICE_CONNECTED)
    		{
			Intent startService = new Intent(this, service);
			startService(startService);
    		}
		Intent bindingIntent = new Intent(this, service);
		bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
		}

	private void sendMessage(String msg)
		{
		Pattern pattern = Pattern.compile("\n$");
		Matcher matcher = pattern.matcher(msg);
		String strResult = matcher.replaceAll("");
		try
    		{
			usbService.write(strResult.getBytes("UTF-8"));
    		}
    		catch (Exception e)
    		{
    		}
		}

	private void addReceivedData(String data)
		{
		receiverTextbox.append(data);
		receiverScrollbar.postDelayed(new Runnable()
			{
		    @Override public void run()
		    	{
		    	receiverScrollbar.fullScroll(ScrollView.FOCUS_DOWN);
		        }
		    }, 100);
		}

	private static class MyHandler extends Handler
		{
		private final WeakReference<Main> mActivity;

		public MyHandler(Main activity)
    		{
			mActivity = new WeakReference<>(activity);
    		}

		@Override public void handleMessage(Message msg)
    		{
			switch (msg.what)
				{
				case AppService.MESSAGE_FROM_SERIAL_PORT:
				String data = (String) msg.obj;
				if (data != null)
            		{
					mActivity.get().addReceivedData(data);
            		}
				break;
            
				default:
				break;
				}
    		}
		}
	
    private final ServiceConnection usbConnection = new ServiceConnection()
    	{
    	@Override public void onServiceConnected(ComponentName arg0, IBinder arg1)
    		{
    		usbService = ((AppService.UsbBinder) arg1).getService();
    		}

    	@Override public void onServiceDisconnected(ComponentName arg0)
    		{
    		usbService = null;
    		}
    	};
	
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
		{
    	@Override public void onReceive(Context context, Intent intent)
    		{
    		switch (intent.getAction())
        		{
        		case AppService.ACTION_USB_PERMISSION_GRANTED:
        		clickInConnect();
        		break;
            
        		case AppService.ACTION_USB_DISCONNECTED:
        		clickInDisconnect();
        		break;
            
        		default:
        		break;
        		}
    		}
		};
	}