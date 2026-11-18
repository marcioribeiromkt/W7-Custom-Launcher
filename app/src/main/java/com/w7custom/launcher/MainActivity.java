package com.w7custom.launcher;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/**
 * W7 Custom Cockpit
 * All third-party web pages run in a separate WebView WITHOUT a JavascriptInterface.
 * The dashboard is a bundled, local asset with a restricted native bridge.
 * No writes to MCU, factory radio, reverse camera or vehicle CAN.
 */
public class MainActivity extends Activity {
    private final String[] labels={"Waze","Google Maps","Spotify","YouTube Music","YouTube","Projeção","Rádio FM","Bluetooth","Aplicativo OBD"};
    private final String[] packages={"com.waze","com.google.android.apps.maps","com.spotify.music","com.google.android.apps.youtube.music","com.google.android.youtube","","","","org.prowl.torque"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView ui,media,map;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastPosition;
    private boolean mapActive=false, mapLoaded=false;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ObdReader obd=new ObdReader();
    private boolean dashboardReady=false,obdConnected=false,mediaActive=false,autoObdStarted=false;
    private String obdStatus="Selecione e conecte seu ELM327";
    private String mediaSource="youtube";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("w7",MODE_PRIVATE);
        getWindow().setStatusBarColor(0xff080e19);
        getWindow().setNavigationBarColor(0xff080e19);
        root=new FrameLayout(this);
        root.setBackgroundColor(0xff080e19);
        setContentView(root);
        ui=new WebView(this);
        ui.setBackgroundColor(0xff080e19);
        WebSettings cfg=ui.getSettings();
        cfg.setJavaScriptEnabled(true);
        cfg.setDomStorageEnabled(true);
        cfg.setAllowFileAccess(true);
        cfg.setAllowFileAccessFromFileURLs(false);
        cfg.setAllowUniversalAccessFromFileURLs(false);
        cfg.setMediaPlaybackRequiresUserGesture(true);
        ui.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                Uri u=request.getUrl();
                return !"file".equalsIgnoreCase(u.getScheme()) || !"/android_asset/dashboard.html".equals(u.getPath());
            }
            @Override public void onPageFinished(WebView v,String url){
                dashboardReady=true;
                refreshObd();
                if(!autoObdStarted&&!prefs.getString("obdMac","").isEmpty()){
                    autoObdStarted=true;handler.postDelayed(()->connectObd(),850);
                }
            }
        });
        ui.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsPrompt(WebView view,String url,String message,String defaultValue,JsPromptResult result){
                EditText edit=new EditText(MainActivity.this);
                edit.setSingleLine(true);
                edit.setText(defaultValue);
                edit.setSelectAllOnFocus(true);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Link de mídia").setMessage(message).setView(edit)
                    .setPositiveButton("Abrir",(d,w)->result.confirm(edit.getText().toString().trim()))
                    .setNegativeButton("Cancelar",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel())
                    .show();
                return true;
            }
        });
        ui.addJavascriptInterface(new Bridge(),"W7");
        root.addView(ui,new FrameLayout.LayoutParams(-1,-1));
        ui.loadUrl("file:///android_asset/dashboard.html");
    }

    private void js(String call){
        handler.post(()->{if(ui!=null&&dashboardReady)ui.evaluateJavascript(call,null);});
    }
    private void refreshObd(){
        try{
            JSONObject j=new JSONObject();
            j.put("message",obdStatus);j.put("connected",obdConnected);
            js("window.W7Bridge&&W7Bridge.onStatus("+j.toString()+")");
        }catch(Exception ignored){}
    }
    private void sendData(int rpm,int kmh,int temp,String voltage){
        try{
            JSONObject j=new JSONObject();
            j.put("rpm",rpm);j.put("kmh",kmh);j.put("temp",temp);j.put("volt",voltage);
            js("window.W7Bridge&&W7Bridge.onData("+j.toString()+")");
        }catch(Exception ignored){}
    }
    private class Bridge {
        @JavascriptInterface public String getSkin(){return prefs.getString("skin","cockpit");}
        @JavascriptInterface public void saveSkin(String skin){
            if("hybrid".equals(skin)||"cockpit".equals(skin))prefs.edit().putString("skin",skin).apply();
        }
        @JavascriptInterface public void openApp(int index){handler.post(()->launch(index));}
        @JavascriptInterface public void configure(int index){handler.post(()->chooseApp(index));}
        @JavascriptInterface public void launchInstalled(String pkg,String cls){
            handler.post(()->startComponent(pkg,cls));
        }
        @JavascriptInterface public String appsJson(){return applicationListJson();}
        @JavascriptInterface public void chooseObd(){handler.post(()->chooseBluetooth());}
        @JavascriptInterface public void connectObd(){handler.post(()->connectObd());}
        @JavascriptInterface public void loadMedia(String kind){handler.post(()->loadMedia(kind));}
        @JavascriptInterface public void loadMediaUrl(String url){handler.post(()->loadMediaUrl(url));}
        @JavascriptInterface public void closeMedia(){handler.post(()->closeMedia());}
        @JavascriptInterface public void showMap(){handler.post(()->showMap());}
        @JavascriptInterface public void hideMap(){handler.post(()->hideMap());}
        @JavascriptInterface public void hideMapOverlay(boolean hidden){handler.post(()->{if(map!=null){map.setVisibility(hidden?View.INVISIBLE:(mapActive?View.VISIBLE:View.GONE));if(!hidden&&mapActive)js("window.updateMapRect&&window.updateMapRect()");}});}
        @JavascriptInterface public void mapRect(double x,double y,double w,double h,double cw,double ch){handler.post(()->setMapPosition(x,y,w,h,cw,ch));}
        @JavascriptInterface public void hideMedia(boolean hidden){handler.post(()->{if(media!=null){media.setVisibility(hidden?View.INVISIBLE:(mediaActive?View.VISIBLE:View.GONE));if(!hidden && mediaActive)js("window.updateMediaRect&&window.updateMediaRect()");}});}
        @JavascriptInterface public void mediaRect(double x,double y,double w,double h,double cw,double ch){
            handler.post(()->setMediaPosition(x,y,w,h,cw,ch));
        }
        @JavascriptInterface public void androidSettings(){handler.post(()->openSettings());}
        @JavascriptInterface public void originalHome(){handler.post(()->originalHome());}
    }

    private void startComponent(String pkg,String cls){
        try{
            if(pkg==null||cls==null||pkg.trim().isEmpty()||cls.trim().isEmpty())throw new Exception("Pacote inválido");
            Intent i=new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName(pkg,cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }catch(Exception e){toast("Não foi possível abrir este aplicativo");}
    }
    private String configured(int index){return prefs.getString("shortcut"+index,"");}
    private void launch(int index){
        if(index<0||index>=labels.length)return;
        try{
            String choice=configured(index);
            Intent i=null;
            if(!choice.isEmpty()){
                ComponentName c=ComponentName.unflattenFromString(choice);
                if(c!=null){
                    i=new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setComponent(c);
                }else i=getPackageManager().getLaunchIntentForPackage(choice);
            }
            if(i==null && !packages[index].isEmpty())i=getPackageManager().getLaunchIntentForPackage(packages[index]);
            if(i==null && index>=5){
                ResolveInfo nativeApp=detectNativeApp(index);
                if(nativeApp!=null){
                    i=new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setClassName(nativeApp.activityInfo.packageName,nativeApp.activityInfo.name);
                }
            }
            if(i==null)throw new ActivityNotFoundException();
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }catch(Exception e){
            new AlertDialog.Builder(this)
                .setTitle(labels[index]+" não está vinculado")
                .setMessage("Escolha o aplicativo que já existe na central. Não será instalado outro aplicativo.")
                .setPositiveButton("Escolher aplicativo",(d,w)->chooseApp(index))
                .setNegativeButton("Cancelar",null).show();
        }
    }
    private ResolveInfo detectNativeApp(int index){
        ArrayList<ResolveInfo> apps=launcherActivities();
        ResolveInfo best=null;int highest=0;
        for(ResolveInfo app:apps){
            if(app.activityInfo==null)continue;
            String name=app.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT);
            String pkg=app.activityInfo.packageName.toLowerCase(Locale.ROOT);
            String all=name+" "+pkg;
            int score=0;
            if(index==5){
                if(all.contains("zlink")||all.contains("tlink")||all.contains("autokit")||all.contains("carlink"))score=10;
                else if(all.contains("carplay")||all.contains("android auto")||all.contains("phonelink")||all.contains("phone link")||all.contains("easyconnection"))score=7;
                else if(all.contains("projeção")||all.contains("projection")||all.contains("mirror"))score=4;
            }else if(index==6){
                if(name.contains("rádio")||name.equals("radio")||name.contains("fm radio"))score=10;
                else if(all.contains("radio"))score=6;
            }else if(index==7){
                if(name.contains("bluetooth")||name.contains("bt phone")||name.contains("bt dial"))score=10;
                else if(name.contains("telefone")||name.equals("phone"))score=6;
            }else if(index==8){
                if(all.contains("torque")||name.contains("car scanner"))score=10;
                else if(name.contains("obd"))score=5;
            }
            if(score>highest){highest=score;best=app;}
        }
        return best;
    }
    private ArrayList<ResolveInfo> launcherActivities(){
        Intent q=new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<ResolveInfo> items=new ArrayList<>(getPackageManager().queryIntentActivities(q,0));
        Collections.sort(items,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        return items;
    }
    private void chooseApp(int index){
        if(index<0||index>=labels.length)return;
        ArrayList<ResolveInfo> apps=launcherActivities();
        String[] names=new String[apps.size()+2];
        names[0]="LIMPAR VÍNCULO / UTILIZAR PADRÃO";
        for(int k=0;k<apps.size();k++)names[k+1]=apps.get(k).loadLabel(getPackageManager()).toString();
        names[names.length-1]="INFORMAR PACOTE MANUALMENTE";
        new AlertDialog.Builder(this).setTitle("Vincular "+labels[index])
            .setItems(names,(d,k)->{
                if(k==0){prefs.edit().remove("shortcut"+index).apply();toast("Vínculo restaurado");}
                else if(k==names.length-1)manualPackage(index);
                else{
                    ResolveInfo ri=apps.get(k-1);
                    ComponentName component=new ComponentName(ri.activityInfo.packageName,ri.activityInfo.name);
                    prefs.edit().putString("shortcut"+index,component.flattenToString()).apply();
                    toast(labels[index]+": "+ri.loadLabel(getPackageManager()));
                }
            }).setNegativeButton("Cancelar",null).show();
    }
    private void manualPackage(int index){
        EditText edit=new EditText(this);
        edit.setSingleLine(true);
        edit.setHint("com.fabricante.aplicativo");
        new AlertDialog.Builder(this).setTitle("Pacote "+labels[index])
            .setView(edit).setPositiveButton("Salvar",(d,w)->{
                String p=edit.getText().toString().trim();
                if(p.matches("[A-Za-z0-9_.]+"))
                    prefs.edit().putString("shortcut"+index,p).apply();
                else toast("Nome de pacote inválido");
            }).setNegativeButton("Cancelar",null).show();
    }
    private String applicationListJson(){
        JSONArray array=new JSONArray();
        try{
            PackageManager pm=getPackageManager();
            ArrayList<ResolveInfo> apps=launcherActivities();
            HashSet<String> seen=new HashSet<>();
            for(ResolveInfo ri:apps){
                if(ri.activityInfo==null)continue;
                String p=ri.activityInfo.packageName,c=ri.activityInfo.name;
                if(!seen.add(p+"/"+c))continue;
                JSONObject app=new JSONObject();
                app.put("label",ri.loadLabel(pm).toString());app.put("pkg",p);app.put("cls",c);
                try{
                    Drawable icon=ri.loadIcon(pm);
                    Bitmap bitmap=Bitmap.createBitmap(60,60,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(bitmap);
                    icon.setBounds(0,0,60,60);icon.draw(canvas);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream(4096);
                    bitmap.compress(Bitmap.CompressFormat.PNG,85,bytes);
                    app.put("icon",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));
                    bitmap.recycle();
                }catch(Exception ignored){app.put("icon","");}
                array.put(app);
                if(array.length()>=100)break;
            }
        }catch(Exception ignored){}
        return array.toString();
    }

    private boolean bluetoothPermission(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},410);
            return false;
        }
        return true;
    }
    private BluetoothDevice deviceForSelected(){
        BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();
        if(bt==null||!bt.isEnabled())return null;
        String mac=prefs.getString("obdMac","");
        BluetoothDevice first=null;
        for(BluetoothDevice device:bt.getBondedDevices()){
            if(device.getAddress().equalsIgnoreCase(mac))return device;
            String name=device.getName();
            if(name!=null){
                String s=name.toUpperCase(Locale.US);
                if(first==null&&(s.contains("OBD")||s.contains("ELM")||s.contains("VLINK")||s.contains("VGATE")))first=device;
            }
        }
        return first;
    }
    private void chooseBluetooth(){
        if(!bluetoothPermission())return;
        try{
            BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();
            if(bt==null){showObdHelp("A central não expõe Bluetooth Android. O Bluetooth do rádio pode ser controlado pela MCU, separado do Android.");return;}
            if(!bt.isEnabled()){showObdHelp("Ative o Bluetooth Android e emparelhe o adaptador ELM327 nas configurações.");return;}
            ArrayList<BluetoothDevice> devices=new ArrayList<>(bt.getBondedDevices());
            if(devices.size()==0){showObdHelp("Nenhum dispositivo emparelhado está visível ao Android. Verifique o pareamento do ELM327 nas configurações Bluetooth da Winca.");return;}
            String[] names=new String[devices.size()];
            for(int i=0;i<devices.size();i++){
                BluetoothDevice d=devices.get(i);names[i]=(d.getName()==null?"Bluetooth":d.getName())+"  ·  "+d.getAddress();
            }
            new AlertDialog.Builder(this).setTitle("Selecione seu ELM327")
                .setItems(names,(dialog,which)->{
                    prefs.edit().putString("obdMac",devices.get(which).getAddress()).apply();
                    connectObd();
                }).setNegativeButton("Cancelar",null).show();
        }catch(Exception e){showObdHelp("Não foi possível listar os dispositivos Bluetooth: "+e.getClass().getSimpleName());}
    }
    private void showObdHelp(String message){
        obdStatus=message;obdConnected=false;refreshObd();
        new AlertDialog.Builder(this).setTitle("Conexão OBD2")
            .setMessage(message+"\n\nSe sua Winca utiliza um Bluetooth exclusivo da MCU, o adaptador pode não estar disponível aos aplicativos Android.")
            .setPositiveButton("Abrir configurações",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Exception e){openSettings();}
            })
            .setNeutralButton("Aplicativo OBD",(d,w)->launch(8))
            .setNegativeButton("Fechar",null).show();
    }
    private void connectObd(){
        if(!bluetoothPermission())return;
        try{
            BluetoothDevice device=deviceForSelected();
            if(device==null){chooseBluetooth();return;}
            obdStatus="Conectando ao ELM327…";obdConnected=false;refreshObd();
            obd.start(device,new ObdReader.Listener(){
                @Override public void onStatus(String text,boolean connected){
                    obdStatus=text;obdConnected=connected;refreshObd();
                }
                @Override public void onData(int rpm,int kmh,int temp,String voltage){
                    obdConnected=true;obdStatus=(rpm<0&&kmh<0&&temp<0)?"ELM327 conectado; ECU sem resposta":"Leitura ativa";
                    refreshObd();sendData(rpm,kmh,temp,voltage);
                }
            });
        }catch(Exception e){showObdHelp("Não foi possível iniciar o ELM327: "+e.getClass().getSimpleName());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==410 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)chooseBluetooth();
        if(request==411 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)startLocation();
    }

    private void ensureMap(){
        if(map!=null)return;
        map=new WebView(this);
        map.setBackgroundColor(0xff102439);
        WebSettings st=map.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setAllowFileAccess(true);
        st.setAllowFileAccessFromFileURLs(false);
        st.setAllowUniversalAccessFromFileURLs(false);
        map.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();
                return !"file".equalsIgnoreCase(u.getScheme()) || !"/android_asset/map.html".equals(u.getPath());
            }
            @Override public void onPageFinished(WebView v,String url){
                mapLoaded=true;
                if(lastPosition!=null)sendPosition(lastPosition);
                js("window.updateMapRect&&window.updateMapRect()");
            }
        });
        root.addView(map,new FrameLayout.LayoutParams(1,1));
        map.setVisibility(View.GONE);
        map.loadUrl("file:///android_asset/map.html");
    }
    private void showMap(){
        ensureMap();mapActive=true;
        map.setVisibility(View.VISIBLE);
        js("window.updateMapRect&&window.updateMapRect()");
        startLocation();
    }
    private void hideMap(){
        mapActive=false;
        if(map!=null)map.setVisibility(View.GONE);
    }
    private void setMapPosition(double x,double y,double w,double h,double cw,double ch){
        if(!mapActive||map==null||map.getVisibility()!=View.VISIBLE||cw<=0||ch<=0||ui==null)return;
        double sx=ui.getWidth()/cw,sy=ui.getHeight()/ch;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.max(1,(int)(w*sx)),Math.max(1,(int)(h*sy)));
        p.leftMargin=(int)(x*sx);p.topMargin=(int)(y*sy);
        map.setLayoutParams(p);map.bringToFront();
    }
    private void sendPosition(Location position){
        if(map==null||!mapLoaded)return;
        final double lat=position.getLatitude(),lon=position.getLongitude(),accuracy=position.getAccuracy();
        if(!Double.isFinite(lat)||!Double.isFinite(lon))return;
        map.evaluateJavascript("window.w7Position&&w7Position("+lat+","+lon+","+accuracy+")",null);
    }
    private void startLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
          &&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},411);
            return;
        }
        try{
            if(locationManager==null)locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(locationManager==null)return;
            if(locationListener==null)locationListener=new LocationListener(){
                @Override public void onLocationChanged(Location location){lastPosition=location;sendPosition(location);}
                @Override public void onStatusChanged(String provider,int status,Bundle extras){}
                @Override public void onProviderEnabled(String provider){}
                @Override public void onProviderDisabled(String provider){}
            };
            locationManager.removeUpdates(locationListener);
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                try{
                    if(locationManager.isProviderEnabled(provider)){
                        Location last=locationManager.getLastKnownLocation(provider);
                        if(last!=null &&(lastPosition==null||last.getTime()>lastPosition.getTime())){lastPosition=last;sendPosition(last);}
                        locationManager.requestLocationUpdates(provider,3000,4f,locationListener);
                    }
                }catch(Exception ignored){}
            }
        }catch(Exception e){toast("Localização indisponível: "+e.getClass().getSimpleName());}
    }
    private void ensureMedia(){
        if(media!=null)return;
        media=new WebView(this);
        media.setBackgroundColor(0xff101a29);
        WebSettings s=media.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setLoadsImagesAutomatically(true);
        media.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();
                String scheme=u.getScheme();
                if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))return false;
                if("intent".equalsIgnoreCase(scheme)){
                    try{startActivity(Intent.parseUri(u.toString(),Intent.URI_INTENT_SCHEME));}
                    catch(Exception ignored){toast("Aplicativo externo indisponível");}
                }
                return true;
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){
                if(req.isForMainFrame())toast("Sem conexão ou página indisponível. Use Abrir aplicativo.");
            }
        });
        media.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(fullscreen!=null){callback.onCustomViewHidden();return;}
                fullscreen=view;fullscreenCallback=callback;
                if(media!=null)media.setVisibility(View.INVISIBLE);
                FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1);
                root.addView(fullscreen,p);
            }
            @Override public void onHideCustomView(){hideFullscreen();}
        });
        CookieManager.getInstance().setAcceptCookie(true);
        if(Build.VERSION.SDK_INT>=21)CookieManager.getInstance().setAcceptThirdPartyCookies(media,true);
        root.addView(media,new FrameLayout.LayoutParams(1,1));
        media.setVisibility(View.GONE);
    }
    private void hideFullscreen(){
        if(fullscreen==null)return;
        root.removeView(fullscreen);fullscreen=null;
        if(fullscreenCallback!=null){fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}
        if(media!=null)media.setVisibility(View.VISIBLE);
    }
    private void loadMedia(String kind){
        String url;
        switch(kind){
            case "spotify":url="https://open.spotify.com/";break;
            case "music":url="https://music.youtube.com/";break;
            default:kind="youtube";url="https://m.youtube.com/";break;
        }
        mediaSource=kind;ensureMedia();mediaActive=true;media.setVisibility(View.VISIBLE);media.loadUrl(url);js("window.updateMediaRect&&window.updateMediaRect()");
    }
    private void loadMediaUrl(String address){
        try{
            Uri uri=Uri.parse(address);
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||host==null)throw new Exception();
            host=host.toLowerCase(Locale.US);
            boolean youtube=host.equals("youtu.be")||host.equals("youtube.com")||host.endsWith(".youtube.com");
            boolean spotify=host.equals("open.spotify.com");
            if(!youtube&&!spotify)throw new Exception();
            String url=uri.toString();
            if(youtube){
                String id=host.equals("youtu.be")?uri.getLastPathSegment():uri.getQueryParameter("v");
                if(id==null && uri.getPathSegments().size()>=2 && uri.getPathSegments().get(0).equals("shorts"))id=uri.getPathSegments().get(1);
                if(id!=null && id.matches("[A-Za-z0-9_-]{11}"))url="https://www.youtube.com/embed/"+id+"?autoplay=0";
            }else{
                List<String> segments=uri.getPathSegments();
                if(segments.size()>=2&&(segments.get(0).equals("track")||segments.get(0).equals("episode")||segments.get(0).equals("playlist"))){
                    url="https://open.spotify.com/embed/"+segments.get(0)+"/"+segments.get(1);
                }
            }
            ensureMedia();mediaActive=true;media.setVisibility(View.VISIBLE);media.loadUrl(url);js("window.updateMediaRect&&window.updateMediaRect()");
        }catch(Exception e){toast("Use um link HTTPS válido do YouTube ou Spotify");}
    }
    private void setMediaPosition(double x,double y,double w,double h,double cssWidth,double cssHeight){
        if(media==null||media.getVisibility()!=View.VISIBLE||cssWidth<=0||cssHeight<=0)return;
        double sx=ui.getWidth()/cssWidth,sy=ui.getHeight()/cssHeight;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams((int)(w*sx),(int)(h*sy));
        p.leftMargin=(int)(x*sx);p.topMargin=(int)(y*sy);
        media.setLayoutParams(p);
        media.bringToFront();
    }
    private void closeMedia(){
        hideFullscreen();mediaActive=false;
        if(media!=null){media.stopLoading();media.loadUrl("about:blank");media.setVisibility(View.GONE);}
    }
    private void openSettings(){
        try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){toast("Configurações indisponíveis");}
    }
    private void originalHome(){
        Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(Intent.CATEGORY_HOME);
        ArrayList<ResolveInfo> homes=new ArrayList<>(getPackageManager().queryIntentActivities(query,0));
        ArrayList<ResolveInfo> available=new ArrayList<>();
        for(ResolveInfo r:homes)if(r.activityInfo!=null && !getPackageName().equals(r.activityInfo.packageName))available.add(r);
        if(available.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Launcher original").setMessage("Não encontrei outro launcher acessível. Abra Aplicativos padrão nas configurações para restaurar a tela inicial da Winca.")
                .setPositiveButton("Configurações",(d,w)->openSettings()).setNegativeButton("Fechar",null).show();
            return;
        }
        String[] names=new String[available.size()];
        for(int i=0;i<available.size();i++)names[i]=available.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("Abrir tela inicial original")
            .setItems(names,(d,i)->{try{Intent home=new Intent(Intent.ACTION_MAIN);home.addCategory(Intent.CATEGORY_HOME);home.setClassName(available.get(i).activityInfo.packageName,available.get(i).activityInfo.name);home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(home);}catch(Exception e){toast("Tela inicial original indisponível");}})
            .setNegativeButton("Cancelar",null).show();
    }
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){
        super.onResume();js("window.W7Bridge&&W7Bridge.onResume()");
    }
    @Override protected void onDestroy(){
        obd.stop();
        if(locationManager!=null&&locationListener!=null)try{locationManager.removeUpdates(locationListener);}catch(Exception ignored){}
        if(map!=null){root.removeView(map);map.destroy();map=null;}
        if(media!=null){closeMedia();root.removeView(media);media.destroy();media=null;}
        if(ui!=null){ui.removeJavascriptInterface("W7");ui.destroy();ui=null;}
        super.onDestroy();
    }
    @Override public void onBackPressed(){
        if(fullscreen!=null){hideFullscreen();return;}
        if(media!=null&&media.getVisibility()==View.VISIBLE&&media.canGoBack()){media.goBack();return;}
        js("window.closeOverlay&&window.closeOverlay()");
    }
}