package com.cis.bluetoothcomm;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendForm;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.YAxis.AxisDependency;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    // Debugging
    private static final String TAG = "MainActivity";
    private String mailTxt = "";
    private int mailCnt = 0;
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_LE_READ = 201;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    //파일저장
    private final int MY_PERMISSION_REQUEST_STORAGE = 100;
    private String Save_Path = Environment.getExternalStorageDirectory().getPath() + File.separator; //getPath() 는 /가 붙어서 옴
    private String Save_folder = "HumanDetectLog";

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 100;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBleClasicService = null;
    private LineChart mLineChart;
    private BleManager mBleLeManager = null;
    private ServiceHandler mServiceHandler = new ServiceHandler();
    TextView liveLabel, avrLabel;
    List<Integer>  avrTotVal = new ArrayList<Integer>();
    private int avrTotCnt = 0;
    private Button mBtnEmail;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage( Message msg )
        {
            //super.handleMessage( msg );
            if( D ) Log.i( TAG, "Handler_msg: " + msg.what );
            switch( msg.what )
            {
                case MESSAGE_STATE_CHANGE:
                    if( D ) Log.i( TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1 );
                    switch( msg.arg1 )
                    {
                        case BluetoothService.STATE_CONNECTED:
                            setStatus( getString(R.string.title_connected_to, mConnectedDeviceName) );
                            //mConversationArrayAdapter.clear();
                            break;

                        case BluetoothService.STATE_CONNECTING:
                            setStatus( R.string.title_connecting );
                            break;

                        case BluetoothService.STATE_LISTEN:
                            break;
                        case BluetoothService.STATE_NONE:
                            setStatus( R.string.title_not_connected );
                            break;
                        case BleManager.STATE_CONNECTED:
                            setStatus( getString(R.string.title_connected_to, mConnectedDeviceName) );
                            break;
                    }
                    break;
                case BleManager.STATE_CONNECTED:
                    setStatus( getString(R.string.title_connected_to, mConnectedDeviceName) );
                    Toast.makeText(getApplicationContext(), "Connected to "+mConnectedDeviceName,Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_READ:
                    /*byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String( readBuf, 0, msg.arg1 );
                    Log.d( TAG, "readMessage : " + readMessage + ", " + HexUtils.hexToString( readBuf, 0, readBuf.length) + ", length : " + readBuf.length );*/
                    if(msg.obj != null) {
                        String readMessage = (String) msg.obj;
                        addValue( readMessage );
                    }
                    break;

                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);

                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;

                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_LE_READ:
                    if(msg.obj != null) {
                        String readMessageLe = (String) msg.obj;
                        Log.d( TAG, "11readMessage : " + msg.obj);
                        addValue( readMessageLe );
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if( D ) Log.e( TAG, "+++ ON CREATE +++" );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        // BLE 관련 Permission 주기
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            // Android M Permission check
            if(this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("이 앱은 BLE 위치 정보 액세스 권한이 있어야합니다.");
                builder.setMessage("이 앱이 BLE를 감지 할 수 있도록 위치 정보 액세스 권한을 부여하십시오.");
                builder.setPositiveButton("Ok", null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @TargetApi(Build.VERSION_CODES.M)
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if( mBluetoothAdapter == null )
        {
            Toast.makeText( this, "Bluetooth is not available", Toast.LENGTH_LONG ).show();
            finish();
            return;
        }
        liveLabel = (TextView)findViewById(R.id.live);
        avrLabel = (TextView)findViewById(R.id.avr);
        mBtnEmail = (Button) findViewById( R.id.btn_email);
        //e-mail 보내기 버튼
        mBtnEmail.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mBtnEmail.setEnabled(false);
                mBtnEmail.setText("ing...");
                mBtnEmail.setBackgroundColor(Color.GRAY);
                mailCnt++;
            }
        });
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();

        if( D ) Log.e( TAG, "++ ON START ++" );

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if( !mBluetoothAdapter.isEnabled() ){
            Intent enableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
            startActivityForResult( enableIntent, REQUEST_ENABLE_BT );
        }else{
            if( mBleClasicService == null || mBleLeManager == null){
                setup();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int reqeustCode, String permission[], int[] grantResults) {
        switch (reqeustCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("permission", "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("기능제한 알림");
                    builder.setMessage("BLE위치 정보 액세스 권한이 부여되지 않았으므로, " +
                            "이 앱은 백그라운드에서 BLE 기기를 발견 할 수 없습니다.");
                    builder.setPositiveButton("Ok", null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {

                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();

        if( D ) Log.e( TAG, "+ ON RESUME +" );

        if( mBleClasicService != null ){
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if( mBleClasicService.getState() == BluetoothService.STATE_NONE ){
                mBleClasicService.start();
            }
        }
    }

    private void setup()
    {
        //Log.d( TAG, "setup()");
        mLineChart = (LineChart) findViewById( R.id.line_chart );

        mLineChart.setDescription( "" );
        mLineChart.setNoDataTextDescription( "No data for the moment" );

        //mLineChart.setHighlightEnabled( true );
        mLineChart.setHighlightPerDragEnabled( true );

        // enable touch gesture
        mLineChart.setTouchEnabled( true );

        // we want also enable scaling and dragging
        mLineChart.setDragEnabled( true );
        mLineChart.setScaleEnabled( true );
        mLineChart.setDrawGridBackground( false );

        // enable pinch zoom to avoid scaling x and y axis seperately
        mLineChart.setPinchZoom( true );

        // alternative background color
        mLineChart.setBackgroundColor( Color.LTGRAY );

        // now, we work on data
        LineData lineData = new LineData();
        //lineData.setHighlightEnabled( true );
        lineData.setValueTextColor( Color.RED );

        // add data to line chart
        mLineChart.setData( lineData );

        // get legend object
        Legend legend = mLineChart.getLegend();

        // customize legend
        legend.setForm( LegendForm.LINE );
        legend.setTextColor( Color.WHITE );

        XAxis x1 = mLineChart.getXAxis();
        x1.setTextColor( Color.WHITE );
        x1.setDrawGridLines( true );
        x1.setEnabled(true);
        x1.setDrawLabels(true);
        //x1.setLabelCount ( 5 , true );
        //x1.setAxisMaxValue( 100f );
        //x1.setAxisLineWidth(100f);
        x1.setAvoidFirstLastClipping( true );

        YAxis y1 = mLineChart.getAxisLeft();
        y1.setTextColor( Color.WHITE );
        //y1.setLabelCount ( 5 , true );
        y1.setAxisMaxValue( 400f );
        y1.setAxisMinValue( 0f );
        y1.setEnabled(true);
        y1.setDrawLabels(true);
        y1.setDrawGridLines( true );

        YAxis y12 = mLineChart.getAxisRight();
        y12.setEnabled( false );

        // Initialize the BluetoothChatService to perform bluetooth connections
        mBleClasicService = new BluetoothService(this, mHandler);
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();

        if( mBleClasicService != null ){
            mBleClasicService.stop();
        }
        if( mBleLeManager != null ){
            mBleLeManager.close();
            mBleLeManager.disconnect();
        }
        System.exit(0); //handeler 러가 죽지 않아서 어플재시작시 화면업데이트가 안됨
        if( D ) Log.e( TAG, "--- ON DESTROY ---" );
    }

    private void ensureDiscoverable()
    {
        if( D ) Log.d( TAG, "ensure discoverable" );

        if( mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE )
        {
            Intent discoverableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE );
            discoverableIntent.putExtra( BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300 );
            startActivity( discoverableIntent );
        }
    }

    private final void setStatus( int resId ){
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle( resId );
    }

    private final void setStatus( CharSequence subTitle ){
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle( subTitle );
    }

    private boolean isNumeric( String str ){
        try{
            double d = Double.parseDouble( str );
        }catch( NumberFormatException e ){
            return false;
        }

        return true;
    }

    private void sendEmail(String val){

        if(3000 < mailCnt){
            //Log.d("SendMail", mailTxt);
            //메일쏘기
            /*final GMailSender sender = new GMailSender("pviivq81","wlapdlf0108"); // SUBSTITUTE HERE
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        sender.sendMail(
                                "HuManDetect Log !!",   //subject.getText().toString(),
                                "111111111",           //body.getText().toString(),
                                "pviivq81@gmail.com",          //from.getText().toString(),
                                "pviivq@naver.com"            //to.getText().toString()
                        );

                    } catch (Exception e) {
                        Log.e("SendMail", e.getMessage(), e);
                    }
                }
            }).start();*/

            String ext = Environment.getExternalStorageState();
            if (ext.equals(Environment.MEDIA_MOUNTED)) {
                File dir = new File(Save_Path + Save_folder);
                // 19(4.4)이상부터는 새로운 코드 적용
                if (Build.VERSION.SDK_INT > 22) {
                    checkPermission();
                } else {
                    // 18이하는 기존코드
                    // 폴더가 존재하지 않을 경우 폴더를 만듦
                    if (!dir.exists()) {
                        dir.mkdir();
                    }
                    // 시스템으로부터 현재시간(ms) 가져오기
                    long now = System.currentTimeMillis();
                    // Data 객체에 시간을 저장한다.
                    Date date = new Date(now);
                    // 각자 사용할 포맷을 정하고 문자열로 만든다.
                    SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
                    String strNow = sdfNow.format(date);
                    // txt 파일 생성
                    File savefile = new File(Environment.getExternalStorageDirectory().getPath() + File.separator+"HumanDetectLog"+"/"+strNow+".txt");
                    try{

                        FileOutputStream fos = new FileOutputStream(savefile);
                        fos.write(mailTxt.getBytes());
                        fos.close();
                        Toast.makeText(this, "Save Success", Toast.LENGTH_SHORT).show();
                    } catch(IOException e){
                    }
                }
            }
            //초기화
            mailTxt = "";
            mailCnt = 0;
            mBtnEmail.setText("1분간저장");
            mBtnEmail.setEnabled(true);
            mBtnEmail.setBackgroundColor(Color.GRAY);
        }else{
            mailTxt = mailTxt + val;
            mailCnt++;
        }
    }
    /**
     * Permission check.
     */
    @TargetApi(23)
    private void checkPermission() {
        //Log.i("aaaa", "CheckPermission : " + checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Explain to the user why we need to write the permission.
                Toast.makeText(this, "Read/Write external storage", Toast.LENGTH_SHORT).show();
            }
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100); //MY_PERMISSION_REQUEST_STORAGE
            // MY_PERMISSION_REQUEST_STORAGE is an
            // app-defined int constant
        } else {
            writeFile();
        }
    }

    /**
     * Create file example.
     * Save_Path = Environment.getExternalStorageDirectory()
     .getAbsolutePath() + Save_folder;
     */
    private void writeFile() {
        String ext = Environment.getExternalStorageState();
        if (ext.equals(Environment.MEDIA_MOUNTED)) {

            File dir = new File(Save_Path + Save_folder);
            // 폴더가 존재하지 않을 경우 폴더를 만듦
            if (!dir.exists()) {
                dir.mkdir();
            }
            // 시스템으로부터 현재시간(ms) 가져오기
            long now = System.currentTimeMillis();
            // Data 객체에 시간을 저장한다.
            Date date = new Date(now);
            // 각자 사용할 포맷을 정하고 문자열로 만든다.
            SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
            String strNow = sdfNow.format(date);
            // txt 파일 생성
            File savefile = new File(Environment.getExternalStorageDirectory().getPath() + File.separator+"HumanDetectLog"+"/CIS_Log_"+strNow+".txt");
            try{
                FileOutputStream fos = new FileOutputStream(savefile);
                fos.write(mailTxt.getBytes());
                fos.close();
                Toast.makeText(this, "Save Success", Toast.LENGTH_SHORT).show();
            } catch(IOException e){
            }
        };
        //Log.d("aaaa", "create new File : " + Environment.getExternalStorageDirectory().getPath() + File.separator + "temp.txt");
    }
    private void addValue( String strValue ){
        //Log.d( TAG, "addValue - strValue : " + strValue );
        //if(0 < mailCnt){
            //sendEmail(strValue);
        //};

        String[] temp;
        //String delimiter = "\r\n";
        String delimiter = ",";
        temp = strValue.split( delimiter ); //0번째 심박수 그래프값 1번째 손가락그래프값 2번째 LIVE값
        //Log.d( TAG, "addValue - strValue : " +  temp.length );
        if(temp.length > 2){
            //Log.d( TAG, "1111111: =" +  temp[1] + "=");
            //int tempVal = Integer.parseInt(temp[1].replaceAll("\n",""));
            if(Float.parseFloat(temp[1]) < 1){
                addEntry(Float.parseFloat(temp[0])*100, 10);    //그래프 추가
                liveLabel.setText("--");
            }else{
                addEntry(Float.parseFloat(temp[0])*100, 300);
                liveLabel.setText(temp[2]);
            }
        }
        //addEntry(Float.parseFloat(temp[0])*100,Float.parseFloat(temp[1])*8);    //그래프 추가
        //liveLabel.setText(temp[1]);
        /*if(2 < temp.length){
            avrCnt(temp[2]);   //60개의 평균값
            float tempLavel = Float.parseFloat(temp[2]);
            if(50f < tempLavel && 130f > tempLavel){
                liveLabel.setText(tempLavel+"");
            }else{
                liveLabel.setText("0");
            }
            //실데이터 2.몇으로 들어옴
            Float fHeart = Float.parseFloat(temp[0])*100;
            Float fHand = (Float.parseFloat(temp[1])*100)-250;
            if(10f > fHand){    //10이하값은 모두 8로 통일
                fHand = 8f;
            }
            //Log.d( TAG, "fHeart:"+fHeart+"     fHand : " + fHand );
            if (150.0f < fHeart) {  //150 이하는 모두 150으로 표현 노이즈 김경석대표님 요청사항
                addEntry(fHeart,fHand);    //그래프 추가
            }else{
                fHeart = 150f;
                addEntry(fHeart,fHand);    //그래프 추가
                Log.d( TAG, "DATA FAIL= fHeart:"+fHeart+"...fHand : " + fHand );
            }
        }*/

        /*
        int tempt = 0;
        Float ftemp = 0.0f;
        String stemp = "";
        for( int i = 0; i < temp.length; i++ ){
            //Log.d( TAG, "temp + " + (i + 1) + " : " + temp[i] + ", Length : " + temp[i].length() + ", Number : " + isNumeric( temp[i]) );
            if(temp[i].equals("") || temp[i].equals(null)){

            }else{
                stemp = temp[i].substring(0,1);
            }


            if(stemp.equals("T")){  //T로시작되는 숫자는 화면에 띄워준다 추후 화면에 업데이트
                Toast.makeText(MainActivity.this, ""+temp[i].substring(1,temp[i].length()), Toast.LENGTH_SHORT).show();
            }else {
                if (isNumeric(temp[i])) {
                    //Log.d( "aaaaa", "temp="+temp[i] );
                    Float f = Float.parseFloat(temp[i]);
                    //addEntry( f );
                    if (f > 150.0f) {  //150 이하는 버린다 노이즈
                        addEntry(f);    //그래프 추가
                    }
                }
            }
        }*/
    }
    private void avrCnt(String avrValue){
        int avrValInt = Integer.parseInt(avrValue.trim());
        double avr = 0;
        if(100 < avrTotCnt){    //초당 1개씩일경우 1분동안의 값
            avrTotVal.remove(0);
            avrTotVal.add(avrValInt);
        }else{
            avrTotVal.add(avrTotCnt, avrValInt);
            avrTotCnt++;
        }
        for( int i = 0; i < avrTotVal.size(); i++ ){
            avr = avr + avrTotVal.get(i);
        }
        if( D ) Log.d( TAG, "avrTotCnt= " + avrTotCnt );
        if( D ) Log.d( TAG, "avrVal= " + avr );
        avr =  Math.round((avr/avrTotCnt)*10d) / 10d;//Math.ceil(avr/avrTotCnt);
        if(50d < avr && 130d > avr){
            avrLabel.setText(""+avr);
        }else{
            avrLabel.setText("0.0");
        }
    }
    private void scanDevice(){
        Intent serverIntent = null;

        serverIntent = new Intent( this, DeviceListActivity.class );
        startActivityForResult( serverIntent, REQUEST_CONNECT_DEVICE );
    }

    private void connectDevice( Intent data ){   //일반 블루투스 연결
        // Get the device MAC address
        String address = data.getExtras().getString( DeviceListActivity.EXTRA_DEVICE_ADDRESS );
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( address );
        Log.d( TAG, "1111111111111 " + address );
        Log.d( TAG, "111111111111111 " + device );
        mBleClasicService.connect( device );
    }

    private void connectLeDevice( Intent data  ) //ble연결
    {
        mBleLeManager = BleManager.getInstance(getApplicationContext(), mServiceHandler);
        String address = data.getExtras().getString( DeviceListActivity.EXTRA_DEVICE_ADDRESS );
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( address );
        mConnectedDeviceName = device.getName();
        mBleLeManager.connectGatt(getApplicationContext(), true, device);
    }

    public void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        if( D ) Log.d( TAG, "onActivityResult " + requestCode );

        switch( requestCode )
        {
            case REQUEST_CONNECT_DEVICE:    //연결성공시
                // When DeviceListActivity returns with a device to connect
                if( resultCode == Activity.RESULT_OK )
                {
                    String address = data.getExtras().getString( DeviceListActivity.EXTRA_DEVICE_ADDRESS );
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( address );

                    int deviceType = device.getType();
                    if( deviceType == BluetoothDevice.DEVICE_TYPE_CLASSIC ){    //일반 블루투스 1
                        connectDevice( data );
                    }else if( deviceType == BluetoothDevice.DEVICE_TYPE_LE ){    //ble 블루투스 2
                        if(this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
                            connectLeDevice( data );
                        }else{
                            Toast.makeText( this, "BLE is not supported", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                break;

            case REQUEST_ENABLE_BT: //블루투스비활성화시
                if( resultCode == Activity.RESULT_OK )
                {
                    // Bluetooth is now enabled, so set up a chat session
                    setup();
                }
                else
                {
                    //Log.d( TAG, "BT not enabled" );
                    Toast.makeText( this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT ).show();
                    finish();
                }
        }
    }

    // 차트 데이터 멀티증가그리기
    private void addEntry( float heart, float hand )
    {
        //Log.d( TAG, "addEntry - value : " + value );

        LineData lineData = mLineChart.getData();

        if( lineData != null ){
            LineDataSet setOne = (LineDataSet) mLineChart.getData().getDataSetByIndex( 0 );
            LineDataSet setTwo = (LineDataSet) mLineChart.getData().getDataSetByIndex( 1 );

            if( setOne == null ){
                // creation if null
                setOne = createDataSet(0);
                lineData.addDataSet( setOne );
            }
            if( setTwo == null){
                // creation if null
                setTwo = createDataSet(1);
                lineData.addDataSet( setTwo );
            }

            // add a new random value
            lineData.addXValue( "" );
            lineData.addEntry( new Entry( heart, setOne.getEntryCount() ), 0 );     //동작센서 라인에 셋팅
            lineData.addEntry( new Entry( hand, setOne.getEntryCount() ), 1 );      //심박 라인에 셋팅
           /* Log.d(TAG, "11111111" + lineData.getDataSetCount());
            if(10 < lineData.getDataSetCount()){
                lineData.removeEntry(0,0);  //지나간 데이터 삭제요청
                lineData.removeEntry(0,1);
            }*/
            // notify chart data have changed
            mLineChart.notifyDataSetChanged();
            mLineChart.invalidate();

            // limit number of visible entries
            mLineChart.setVisibleXRangeMaximum( 200 );
            // scroll to the last entry

            mLineChart.moveViewToX( lineData.getXValCount() - 7 );
        }
    }

    // 차트 옵션 셋팅
    private LineDataSet createDataSet(int indexCnt)
    {
        LineDataSet lineDataSet;
        if( 0 == indexCnt){
            lineDataSet = new LineDataSet( null, "data" );
            lineDataSet.setHighLightColor( Color.RED );
            lineDataSet.setColor( Color.RED );
            lineDataSet.setCircleColor( Color.RED );
        }else{
            lineDataSet = new LineDataSet( null, "Logic" );
            lineDataSet.setHighLightColor( ColorTemplate.getHoloBlue() );
            lineDataSet.setColor( ColorTemplate.getHoloBlue() );
            lineDataSet.setCircleColor( ColorTemplate.getHoloBlue() );
        }
        lineDataSet.setDrawCubic( true );
        lineDataSet.setCubicIntensity( 0.2f );
        lineDataSet.setAxisDependency( AxisDependency.LEFT );
        lineDataSet.setLineWidth( 2f );
        lineDataSet.setCircleSize( 4f );
        lineDataSet.setFillAlpha( 65 );
        lineDataSet.setFillColor( ColorTemplate.getHoloBlue() );
        lineDataSet.setValueTextColor( Color.WHITE );
        lineDataSet.setValueTextSize( 0.1f );   //점포인트 상단 vaule 크기
        lineDataSet.setCircleRadius(0.1f);      //점포인트 크기
        return lineDataSet;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {   //상단 메뉴 클릭시

        Intent serverIntent = null;
        switch( item.getItemId() )
        {
            case R.id.connect_scan:
                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent( this, DeviceListActivity.class );
                startActivityForResult( serverIntent, REQUEST_CONNECT_DEVICE );
                return true;

            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
        }
        return false;
    }

    //블루투스 LE 핸들러 BleManager에서 받는곳
    class ServiceHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {

            switch(msg.what) {
                // Bluetooth state changed
                case BleManager.MESSAGE_STATE_CHANGE:
                    // Bluetooth state Changed
                    Log.d(TAG, "Service - MESSAGE_STATE_CHANGE: " + msg.arg1);

                    switch (msg.arg1) {
                        case BleManager.STATE_NONE:
                            //mActivityHandler.obtainMessage(Constants.MESSAGE_BT_STATE_INITIALIZED).sendToTarget();
                            mHandler.obtainMessage(Constants.MESSAGE_BT_STATE_INITIALIZED).sendToTarget();
                            break;

                        case BleManager.STATE_CONNECTING:
                            mHandler.obtainMessage(Constants.MESSAGE_BT_STATE_CONNECTING).sendToTarget();
                            break;

                        case BleManager.STATE_CONNECTED:
                            mHandler.obtainMessage(16).sendToTarget();
                            break;

                        case BleManager.STATE_IDLE:
                            mHandler.obtainMessage(Constants.MESSAGE_BT_STATE_INITIALIZED).sendToTarget();
                            break;
                    }
                    break;

                // If you want to send data to remote
                case BleManager.MESSAGE_WRITE:
                    Log.d(TAG, "Service - MESSAGE_WRITE: ");
                    String message = (String) msg.obj;
                    /*if(message != null && message.length() > 0)
                        sendMessageToDevice(message);*/
                    break;

                // Received packets from remote
                case BleManager.MESSAGE_READ:
                    String strMsg = (String) msg.obj;
                    // send bytes in the buffer to activity
                    //Log.d(TAG, "Service - MESSAGE_READ: "+strMsg);
                    if(strMsg != null && strMsg.length() > 0) {
                        //mActivityHandler.obtainMessage(Constants.MESSAGE_READ_CHAT_DATA, strMsg).sendToTarget();
                        mHandler.obtainMessage(Constants.MESSAGE_READ_CHAT_DATA, strMsg).sendToTarget();
                    }
                    break;

                case BleManager.MESSAGE_DEVICE_NAME:
                    Log.d(TAG, "Service - MESSAGE_DEVICE_NAME: ");

                    // save connected device's name and notify using toast
                    String deviceAddress = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_ADDRESS);
                    String deviceName = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_NAME);
                    mConnectedDeviceName = deviceName;
                    if(deviceName != null && deviceAddress != null) {
                        // Remember device's address and name
                        /*mConnectionInfo.setDeviceAddress(deviceAddress);
                        mConnectionInfo.setDeviceName(deviceName);*/
                        //setStatus( getString(R.string.title_connected_to, deviceName) );
                        Toast.makeText(getApplicationContext(),
                                "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;

                case BleManager.MESSAGE_TOAST:
                    Log.d(TAG, "Service - MESSAGE_TOAST: ");

                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;

            }	// End of switch(msg.what)

            super.handleMessage(msg);
        }
    }	// End of class MainHandler
}
