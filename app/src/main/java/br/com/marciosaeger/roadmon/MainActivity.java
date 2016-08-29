package br.com.marciosaeger.roadmon;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    public static int DEFINED_DELAY = SensorManager.SENSOR_DELAY_NORMAL;

    public static final float ALPHA = 0.2f;

    private SensorManager senSensorManager;
    private LocationManager locationManager;
    private Sensor senAccelerometer;
    private Sensor senGyroscope;
    private SensorEventListener acelerometerListener;
    private SensorEventListener gyroscopeListener;
    private LocationListener locationListener;
    private float[] accelerometerValues, movingAverageValues, evtValues;
    private boolean allowStoreData = false;
    private boolean gpsFix = false;
    private int movingAverageCount = 0, subset = 6, verifyFirstValues = 0;
    private File roadmonFolder, roadmonFile;
    private double latitude, longitude;
    private StringBuffer accelerometerSb = new StringBuffer();
    private String line=null;
    private FileWriter writer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        movingAverageValues = new float[3];

        //Configurações do RadioGroup de Frequencia
        RadioButton.class.cast(findViewById(R.id.radioNormal)).setChecked(true);
        RadioGroup.class.cast(findViewById(R.id.rgFrequency)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (RadioButton.class.cast(findViewById(R.id.radioFastest)).getId() == i) {
                    DEFINED_DELAY = SensorManager.SENSOR_DELAY_FASTEST;
                } else if (RadioButton.class.cast(findViewById(R.id.radioGame)).getId() == i) {
                    DEFINED_DELAY = SensorManager.SENSOR_DELAY_GAME;
                } else if (RadioButton.class.cast(findViewById(R.id.radioNormal)).getId() == i) {
                    DEFINED_DELAY = SensorManager.SENSOR_DELAY_NORMAL;
                } else {
                    DEFINED_DELAY = SensorManager.SENSOR_DELAY_UI;
                }
                senSensorManager.unregisterListener(acelerometerListener);
                senSensorManager.registerListener(acelerometerListener, senAccelerometer, DEFINED_DELAY);
                senSensorManager.unregisterListener(gyroscopeListener);
                senSensorManager.registerListener(gyroscopeListener, senGyroscope, DEFINED_DELAY);
                Snackbar.make(findViewById(R.id.btnRecord), "Frequencia alterada com sucesso", Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        });

        //Botão de Gravação
        FloatingActionButton btnRecord = (FloatingActionButton) findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                allowStoreData = true;
                // centraliza todas as operações para abertura do arquivo de saída
                openFile();
                Snackbar.make(view, "Gravação iniciada!", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        //Botão de Parar
        FloatingActionButton btnStop = (FloatingActionButton) findViewById(R.id.btnStop);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                allowStoreData = false;
                closeFile();
                showMessage("Gravação finalizada!");
                sendResultsByEmail();
            }
        });

        //Inicialização dos sensores
        senSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION); //Valores do acelerometro sem a gravidade, mas muitos picos de variações são coletados com o dispositivo parado.
        senGyroscope = senSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //Inicializar Listener do Acelerômetro
        acelerometerListener = getAcelerometerListener();
        senSensorManager.registerListener(acelerometerListener, senAccelerometer, DEFINED_DELAY);

        //Inicializar Listener do Giroscópio
        gyroscopeListener = getGyrscopeListener();
        senSensorManager.registerListener(gyroscopeListener, senGyroscope, DEFINED_DELAY);

        // Define a listener that responds to location updates
        locationListener = getLocationListener();

        //Testa se a permissão via manifesto foi aceita ou se precisa ser dada explicitamente
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 11);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
        }
/*
        //File should be on External storage device or created in External storage device
        roadmonFolder = new File(Environment.getExternalStorageDirectory(), "RoadmonFiles");
        if(!roadmonFolder.isDirectory()) {
            roadmonFolder.mkdirs();
        }
        DateFormat df = new SimpleDateFormat("dd_MM_yyyy");
        String date = df.format(Calendar.getInstance().getTime());
        roadmonFile = new File(roadmonFolder, "Leituras_" + date + ".txt");
*/

    }

    private void sendResultsByEmail() {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        // set the type to 'email'
        emailIntent .setType("vnd.android.cursor.dir/email");

        String to[] = {"matheus_barcellos@hotmail.com", "marcio@marciosaeger.com.br", "jorge@unifacs.br"};
        emailIntent .putExtra(Intent.EXTRA_EMAIL, to);
        // the attachment
        if (roadmonFile.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(roadmonFile));
        }
        // the mail subject
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Sensors Result");

        startActivity(Intent.createChooser(emailIntent , "Send email..."));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //Obtém o listener do acelerômetro
    public SensorEventListener getAcelerometerListener() {
        return new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent evt) {
// Este código faz com que latitude e longitude sejam sempre 0 no arquivo
//                if (gpsFix) {
//                    latitude = 0;
//                    longitude = 0;
//                }
                applyFilters(evt);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
    }

    //Obtém o listener do girscópio
    public SensorEventListener getGyrscopeListener() {
        return new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent evt) {
                TextView.class.cast(findViewById(R.id.txtGyroX)).setText("X-Axis: " + evt.values[0]);
                TextView.class.cast(findViewById(R.id.txtGyroY)).setText("Y-Axis: " + evt.values[1]);
                TextView.class.cast(findViewById(R.id.txtGyroZ)).setText("Z-Axis: " + evt.values[2]);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
    }

    //Obtém o listener do GPS
    public LocationListener getLocationListener() {
        return new LocationListener() {
            public void onLocationChanged(Location location) {
                gpsFix = true;
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                TextView.class.cast(findViewById(R.id.txtLat)).setText("Latitude: " + location.getLatitude());
                TextView.class.cast(findViewById(R.id.txtLong)).setText("Longitude: " + location.getLongitude());
                TextView.class.cast(findViewById(R.id.txtSpeed)).setText("Speed: " + location.getSpeed());
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {
                showMessage("Provider: " + provider + " ligado!");
            }

            public void onProviderDisabled(String provider) {
                showMessage("Provider: " + provider + " desligado!");
            }
        };
    }

    //Função utilizada somente no primeiro uso (logo após a instalação), acionada quando o popup de permissão aparece para o usuário
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 10: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                    }
                }
            }
        }
    }

    public void applyFilters(SensorEvent evt) {
        evtValues = evt.values.clone();

        //Low-pass filter
        if (verifyFirstValues != 0){
            for (int i = 0; i < evtValues.length; i++) {
                accelerometerValues[i] = accelerometerValues[i] + ALPHA * (evtValues[i] - accelerometerValues[i]);
            }
        } else {
            verifyFirstValues = 1;
            accelerometerValues = evtValues;
        }

        //Moving Average filter
        if (movingAverageCount == 0) {
            movingAverageCount++;
            movingAverageValues[0] = accelerometerValues[0];
            movingAverageValues[1] = accelerometerValues[1];
            movingAverageValues[2] = accelerometerValues[2];
        }
        else if (movingAverageCount < subset) {
            movingAverageCount++;
            movingAverageValues[0] += accelerometerValues[0];
            movingAverageValues[1] += accelerometerValues[1];
            movingAverageValues[2] += accelerometerValues[2];
        }else {
            movingAverageCount = 0;
            movingAverageValues[0] = movingAverageValues[0] / subset;
            movingAverageValues[1] = movingAverageValues[1] / subset;
            movingAverageValues[2] = movingAverageValues[2] / subset;
            updateAccelerometerFields(movingAverageValues);
        }
    }

    // Função para atualizar os campos do acelerometro
    public void updateAccelerometerFields(float[] values) {
        long curTime = System.currentTimeMillis();

        if (allowStoreData) {
            // verifica se possui um fix do GPS
            if (gpsFix) {
                // se já possui um coord, imprime a linha no arquivo
                String line=values[0]+";"+values[1]+";"+values[2]+";"+curTime+";"+latitude+";"+longitude+System.getProperty("line.separator");
                writeInOpenFile(line);
                latitude = 0d;
                longitude = 0d;
            }
        }

        TextView.class.cast(findViewById(R.id.txtAcelerometroX)).setText("X: " + values[0]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroY)).setText("Y: " + values[1]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroZ)).setText("Z: " + values[2]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroTimestamp)).setText("Timestamp: " + curTime);
    }

    // abre o arquivo
    public void openFile() {
        //File should be on External storage device or created in External storage device
        // Cria ou abre pasta da aplicação na área de armazenamento externa
        roadmonFolder = new File(Environment.getExternalStorageDirectory(), "RoadmonFiles");
        if(!roadmonFolder.isDirectory()) {
            roadmonFolder.mkdirs();
        }
        DateFormat df = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
        String date = df.format(Calendar.getInstance().getTime());
        roadmonFile = new File(roadmonFolder, "RoadMon Readings" + date + ".txt");
        try {
            writer = new FileWriter(roadmonFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void writeInOpenFile(String line) {
        try {
            writer.append(line);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void closeFile() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Exibe toast
    public void showMessage(String message){
        Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
    }
}
