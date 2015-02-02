package com.ugopiemontese.openband;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.ugopiemontese.openband.colorpicker.ColorPickerDialog;
import com.ugopiemontese.openband.preferences.Application;
import com.ugopiemontese.openband.preferences.UserPreferences;
import com.melnykov.fab.FloatingActionButton;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MiOverviewActivity extends Activity
{
	private final String TAG = this.getClass().getSimpleName();

	private UserPreferences userPreferences;

	private ApplicationArrayAdapter mAppArrayAdapter;

	private ListView mListView;

	private class AddDialog extends AlertDialog
	{
		protected AddDialog(Context context)
		{
			super(context);

			setTitle("Select an app");

			final ApplicationArrayAdapter arrayAdapter = new ApplicationArrayAdapter(MiOverviewActivity.this, R.layout.list_row_layout, getApps());
			final ListView listView = new ListView(getContext());
			setView(listView);
			listView.setAdapter(arrayAdapter);
			listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
			{
				@Override public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id)
				{
					Application application = arrayAdapter.getItem(position);
					Intent intent = new Intent(getApplicationContext(), AppPreferenceActivity.class);
					intent.putExtra("packageName", application.getmPackageName());
					intent.putExtra("isNew", true);
					startActivity(intent);
				}
			});
		}
	}

	;

	private AddDialog addDialog;

	private View.OnClickListener mAddButtonListener = new View.OnClickListener()
	{
		public void onClick(View v)
		{
			addDialog.show();
		}
	};

	private AdapterView.OnItemLongClickListener mItemLongClickListerer = new AdapterView.OnItemLongClickListener()
	{
		public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id)
		{
			new AlertDialog.Builder(MiOverviewActivity.this)
					.setTitle("Delete?")
					.setPositiveButton("Yes", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							final String packageName = ((Application) mListView.getItemAtPosition(position)).getmPackageName();
							userPreferences.removeApp(packageName);
							userPreferences.savePreferences(getPreferencesOutputStream());
							mAppArrayAdapter.clear();
							mAppArrayAdapter.addAll(userPreferences.getAppArray());
							mAppArrayAdapter.notifyDataSetChanged();
						}

					})
					.setNegativeButton("No", null)
					.show();

			return true;
		}
	};

	private AdapterView.OnItemClickListener mItemClickListerer = new AdapterView.OnItemClickListener()
	{
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			final String packageName = ((Application) mListView.getItemAtPosition(position)).getmPackageName();

			Intent intent = new Intent(getApplicationContext(), AppPreferenceActivity.class);
			intent.putExtra("packageName", packageName);
			startActivity(intent);
		}
	};

	private void vibrate(final long duration)
	{
		Intent intent = new Intent("vibrate");
		intent.putExtra("duration", duration);
		LocalBroadcastManager.getInstance(MiOverviewActivity.this).sendBroadcast(intent);
	}

	private void setColour(int r, int g, int b)
	{
		Intent intent = new Intent("colour");
		intent.putExtra("red", r);
		intent.putExtra("green", g);
		intent.putExtra("blue", b);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private boolean isEnabled()
	{
		String pkgName = getPackageName();
		final String flat = Settings.Secure.getString(getContentResolver(),
				"enabled_notification_listeners");
		if(!TextUtils.isEmpty(flat))
		{
			final String[] names = flat.split(":");
			for(int i = 0; i < names.length; i++)
			{
				final ComponentName cn = ComponentName.unflattenFromString(names[i]);
				if(cn != null)
				{
					if(TextUtils.equals(pkgName, cn.getPackageName()))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	private FileOutputStream getPreferencesOutputStream()
	{
		try
		{
			return openFileOutput(UserPreferences.FILE_NAME, Context.MODE_PRIVATE);
		}
		catch(FileNotFoundException e)
		{
			try
			{
				new UserPreferences().savePreferences(openFileOutput(UserPreferences.FILE_NAME, Context.MODE_PRIVATE));
			}
			catch(FileNotFoundException ignored)
			{
			}
		}
		return null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_mi_overview);
		findViewById(R.id.fab).setOnClickListener(mAddButtonListener);

		final Intent serviceIntent = new Intent(this, MiBandCommunicationService.class);
		startService(serviceIntent);

		try
		{
			UserPreferences.loadPreferences(openFileInput(UserPreferences.FILE_NAME));
		}
		catch(FileNotFoundException e)
		{
			new UserPreferences().savePreferences(getPreferencesOutputStream());
		}

		userPreferences = UserPreferences.getInstance();

		final List<Application> appArray = userPreferences.getAppArray();
		final PackageManager pm = getPackageManager();

		for(Application app : appArray)
		{
			try
			{
				app.setmAppName(pm.getApplicationLabel(pm.getApplicationInfo(app.getmPackageName(), PackageManager.GET_META_DATA)).toString());
			}
			catch(PackageManager.NameNotFoundException ignored)
			{

			}
		}

		Collections.sort(appArray, new Comparator()
		{
			@Override
			public int compare(Object lhs, Object rhs)
			{
				if(lhs instanceof Application && rhs instanceof Application)
				{
					return ((Application) lhs).getmAppName().compareTo(((Application) rhs).getmAppName());
				}
				else
				{
					return 0;
				}
			}
		});

		mAppArrayAdapter = new ApplicationArrayAdapter(MiOverviewActivity.this, R.layout.list_row_layout, appArray);
		mListView = ((ListView) findViewById(R.id.listView));
		mListView.setAdapter(mAppArrayAdapter);
		mListView.setOnItemLongClickListener(mItemLongClickListerer);
		mListView.setOnItemClickListener(mItemClickListerer);

		final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
		fab.attachToListView(mListView);

        getActionBar().setDisplayHomeAsUpEnabled(true);

		addDialog = new AddDialog(this);
	}

	@Override
	public void onResume()
	{
		super.onResume();
        mAppArrayAdapter.clear();
        mAppArrayAdapter.addAll(userPreferences.getAppArray());
        mAppArrayAdapter.notifyDataSetChanged();

		try
		{
			if(!BluetoothAdapter.getDefaultAdapter().isEnabled())
			{
                new AlertDialog.Builder(this)
                        .setMessage("Please enable Bluetooth")
                        .setTitle("Bluetooth disabled")
                        .setCancelable(true)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int id)
                                    {
                                        Intent intentBluetooth = new Intent();
                                        intentBluetooth.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                                        startActivityForResult(intentBluetooth, 1010);
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int id)
                                    {
                                        dialog.dismiss();
                                    }
                                })
                        .create().show();
			}

			boolean isEnabledNLS = isEnabled();
			if(!isEnabledNLS)
			{
				showConfirmDialog();
			}
		}
		catch(NullPointerException e)
		{
			Toast.makeText(getApplicationContext(), "No Bluetooth device available", Toast.LENGTH_SHORT).show();
		}
	}

	private void showConfirmDialog()
	{
		new AlertDialog.Builder(this)
				.setMessage("Please enable notification access")
				.setTitle("Notification Access")
				.setCancelable(true)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
							}
						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								dialog.dismiss();
							}
						})
				.create().show();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		addDialog.dismiss();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_overview, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
            case android.R.id.home:
                onBackPressed();
                break;
			case R.id.action_vibrate:
				vibrate(50L);
				break;
			case R.id.action_ledcolor:
				new ColorPickerDialog(MiOverviewActivity.this, userPreferences.getmBandColour(), new ColorPickerDialog.OnColorSelectedListener()
				{
					@Override public void onColorSelected(int rgb)
					{
						Log.i(TAG, "" + rgb);
						final int red = ((rgb >> 16) & 0x0ff) / 42;
						final int green = ((rgb >> 8) & 0x0ff) / 42;
						final int blue = ((rgb) & 0x0ff) / 42;

						setColour(red, green, blue);
						userPreferences.setmBandColour(rgb);
						userPreferences.savePreferences(getPreferencesOutputStream());
					}
				}).show();
				break;
		}
		return true;
	}

    @Override
    public void onBackPressed() {
        finish();
    }

	private ArrayList<Application> getApps()
	{
		final ArrayList<Application> toRet = new ArrayList<>();
		final PackageManager pm = getPackageManager();

		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		for(ApplicationInfo packageInfo : packages)
		{
			if(userPreferences.getApp(packageInfo.packageName) == null)
			{
				toRet.add(new Application(packageInfo.packageName, pm.getApplicationLabel(packageInfo).toString()));
			}
		}

		Collections.sort(toRet, new Comparator()
		{
			@Override
			public int compare(Object lhs, Object rhs)
			{
				return ((Application) lhs).getmAppName().compareToIgnoreCase(((Application) rhs).getmAppName());
			}
		});
		return toRet;
	}
}
