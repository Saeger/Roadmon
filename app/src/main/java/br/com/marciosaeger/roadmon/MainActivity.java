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
    private Location lastKnowLocation;
    private float[] accelerometerValues, movingAverageValues;
    private boolean allowStoreData = false;
    private int movingAverageCount = 0, subset = 6, i;
    File filesFolder, accelerometerFile, gpsFile, gyroFile; //Coloquei global pra poder acessar os arquivos no botão parar

    private StringBuffer accelerometerSb = new StringBuffer();
    private StringBuffer gpsSb = new StringBuffer();
    private StringBuffer gyroSb = new StringBuffer();

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
                FileWriter writer = null;
                try {
                    writer = new FileWriter(accelerometerFile);
                    writer.append(accelerometerSb.toString());
                    writer.flush();
                    writer.close();

                    writer = new FileWriter(gpsFile);
                    writer.append(gpsSb.toString());
                    writer.flush();
                    writer.close();

                    writer = new FileWriter(gyroFile);
                    writer.append(gyroSb.toString());
                    writer.flush();
                    writer.close();

                    Snackbar.make(view, "Gravação finalizada!", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

                    sendResultsByEmail();
                } catch (Exception e) {
                    Snackbar.make(view, "Erro na gravação: " + e.getMessage(), Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
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
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10); //10 é o código representativo(que foi inventado, mudar para variável, caso a implementação seja aceita) para a permissão. Quando a pessoa aceitar, vai para onRequestPermissionsResult
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            lastKnowLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (lastKnowLocation != null) {
                TextView.class.cast(findViewById(R.id.txtLat)).setText("Latitude: " + lastKnowLocation.getLatitude());
                TextView.class.cast(findViewById(R.id.txtLong)).setText("Longitude: " + lastKnowLocation.getLongitude());
                TextView.class.cast(findViewById(R.id.txtSpeed)).setText("Speed: " + lastKnowLocation.getSpeed());
                TextView.class.cast(findViewById(R.id.txtGpsTimestamp)).setText("Timestamp: " + System.currentTimeMillis());
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 11);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
        }
        //File should be on External storage device or created in External storage device
        filesFolder = new File(Environment.getExternalStorageDirectory(), "RoadmonFiles");
        if(!filesFolder.isDirectory()) {
            filesFolder.mkdirs();
        }

        accelerometerFile = new File(filesFolder, "accelerometer.txt");
        gpsFile = new File(filesFolder, "gps.txt");
        gyroFile = new File(filesFolder, "gyro.txt");
    }

    private void sendResultsByEmail() {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        // set the type to 'email'
        emailIntent .setType("vnd.android.cursor.dir/email");

        String to[] = {"matheus_barcellos@hotmail.com"};
        emailIntent .putExtra(Intent.EXTRA_EMAIL, to);
        // the attachment
        if (accelerometerFile.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(accelerometerFile));
        }

        if (gpsFile.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(gpsFile));
        }
        if (gyroFile.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(gyroFile));
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
                accelerometerValues = lowPassAccelerometerFilter(evt.values.clone(), accelerometerValues);
                movingAverageFilter(accelerometerValues);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };
    }

    //Obtém o listener do girscópio
    public SensorEventListener getGyrscopeListener() {
        return new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent evt) {
                long curTime = System.currentTimeMillis();
                if (allowStoreData) {
                    gyroSb.append(evt.values[0] + ","+ evt.values[1] + "," + evt.values[2] + "," + curTime + System.getProperty("line.separator"));
                }

                TextView.class.cast(findViewById(R.id.txtGyroX)).setText("X-Axis: " + evt.values[0]);
                TextView.class.cast(findViewById(R.id.txtGyroY)).setText("Y-Axis: " + evt.values[1]);
                TextView.class.cast(findViewById(R.id.txtGyroZ)).setText("Z-Axis: " + evt.values[2]);
                TextView.class.cast(findViewById(R.id.txtGyroTimestamp)).setText("Timestamp: " + curTime);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };
    }

    //Obtém o listener do GPS
    public LocationListener getLocationListener() {
        return new LocationListener() {
            public void onLocationChanged(Location location) {
                long curTime = System.currentTimeMillis();
                if (allowStoreData) {
                    gpsSb.append(location.getLatitude() + ","+ location.getLongitude() + "," + location.getSpeed() + "," + curTime + System.getProperty("line.separator"));
                }

                TextView.class.cast(findViewById(R.id.txtLat)).setText("Latitude: " + location.getLatitude());
                TextView.class.cast(findViewById(R.id.txtLong)).setText("Longitude: " + location.getLongitude());
                TextView.class.cast(findViewById(R.id.txtSpeed)).setText("Speed: " + location.getSpeed());
                TextView.class.cast(findViewById(R.id.txtGpsTimestamp)).setText("Timestamp: " + curTime);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
                Toast.makeText(getBaseContext(), "Provider: " + provider + " ligado!",
                        Toast.LENGTH_SHORT).show();
            }

            public void onProviderDisabled(String provider) {
                //Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                //startActivity(intent);
                Toast.makeText(getBaseContext(), "Provider: " + provider + " desligado!",
                        Toast.LENGTH_SHORT).show();
            }
        };
    }

    //Função utilizada somente no primeiro uso (logo após a instalação), acionada quando o popup de permissão aparece para o usuário
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 10: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    //If desnecessário, porque nós temos certeza que a permissão foi concedida no if superior, mas sem ele dois graves warnings são exibidos.
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                        lastKnowLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }

                    if (lastKnowLocation != null) {
                        TextView.class.cast(findViewById(R.id.txtLat)).setText("Latitude: " + lastKnowLocation.getLatitude());
                        TextView.class.cast(findViewById(R.id.txtLong)).setText("Longitude: " + lastKnowLocation.getLongitude());
                        TextView.class.cast(findViewById(R.id.txtSpeed)).setText("Speed: " + lastKnowLocation.getSpeed());
                        TextView.class.cast(findViewById(R.id.txtGpsTimestamp)).setText("Timestamp: " + System.currentTimeMillis());
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            case 11: { //READ
                //Do Nothing
            }

            case 12: { //WRITE
                //Do Nothing
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    //Função para aplicar o Low-Pass filter
    public float[] lowPassAccelerometerFilter(float[] input, float[] output) {
        if (output == null) {
            return input;
        }
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    public void movingAverageFilter(float[] evt) {
        if (movingAverageCount == 0) {
            movingAverageCount++;
            movingAverageValues[0] = evt[0];
            movingAverageValues[1] = evt[1];
            movingAverageValues[2] = evt[2];
        }
        else if (movingAverageCount < subset) {
            movingAverageCount++;
            movingAverageValues[0] += evt[0];
            movingAverageValues[1] += evt[1];
            movingAverageValues[2] += evt[2];
        }else {
            movingAverageCount = 0;
            movingAverageValues[0] = movingAverageValues[0] / 6;
            movingAverageValues[1] = movingAverageValues[1] / 6;
            movingAverageValues[2] = movingAverageValues[2] / 6;
            updateAccelerometerFields(movingAverageValues);
        }
    }

    // Função para atualizar os campos do acelerometro
    public void updateAccelerometerFields(float[] values) {
        long curTime = System.currentTimeMillis();
        /*if (allowStoreData) {
            accelerometerSb.append(evt.values[0] + ","+ evt.values[1] + "," + evt.values[2] + "," + curTime + System.getProperty("line.separator"));
        }*/
        if (allowStoreData) {
            accelerometerSb.append(values[0] + ","+ values[1] + "," + values[2] + "," + curTime + System.getProperty("line.separator"));
        }

        TextView.class.cast(findViewById(R.id.txtAcelerometroX)).setText("X: " + values[0]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroY)).setText("Y: " + values[1]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroZ)).setText("Z: " + values[2]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroTimestamp)).setText("Timestamp: " + curTime);

        /*TextView.class.cast(findViewById(R.id.txtAcelerometroX)).setText("X: " + evt.values[0]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroY)).setText("Y: " + evt.values[1]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroZ)).setText("Z: " + evt.values[2]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroTimestamp)).setText("Timestamp: " + curTime);*/

        //Deixei comentado seu filtro porque estava me atrapalhando na visualização dos resultados.
        /*accelerometerValues = lowPassAccelerometerFilter(event.values.clone(), accelerometerValues);
        TextView.class.cast(findViewById(R.id.txtAcelerometroX)).setText("X: " + accelerometerValues[0]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroY)).setText("Y: " + accelerometerValues[1]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroZ)).setText("Z: " + accelerometerValues[2]);
        TextView.class.cast(findViewById(R.id.txtAcelerometroTimestamp)).setText("Timestamp: " + System.currentTimeMillis());*/
    }
}