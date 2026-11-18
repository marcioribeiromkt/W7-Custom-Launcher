package com.w7custom.launcher;

import android.bluetooth.*;
import android.os.*;
import java.io.*;
import java.util.*;

/** ELM327 Bluetooth Classic/SPP. All I/O occurs off the UI thread. */
public class ObdReader {
    public interface Listener {
        void onStatus(String message, boolean connected);
        void onData(int rpm, int kmh, int coolantC, String voltage);
    }
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile BluetoothSocket socket;
    private int generation;
    private void status(Listener l,String msg,boolean connected){ui.post(()->l.onStatus(msg,connected));}
    private void data(Listener l,int rpm,int speed,int coolant,String volts){ui.post(()->l.onData(rpm,speed,coolant,volts));}
    public synchronized void stop(){
        running=false; generation++;
        BluetoothSocket old=socket;socket=null;
        if(old!=null)try{old.close();}catch(IOException ignored){}
    }
    public synchronized void start(BluetoothDevice device, Listener listener) {
        stop(); running=true;final int gen=generation;
        new Thread(()->run(device,listener,gen),"W7-ELM327").start();
    }
    private boolean live(int gen){return running && gen==generation;}
    private String command(InputStream in,OutputStream out,String s,int gen) throws Exception {
        while(in.available()>0)in.read();
        out.write((s+"\r").getBytes("US-ASCII"));out.flush();
        StringBuilder reply=new StringBuilder();
        long deadline=System.currentTimeMillis()+2300;
        while(live(gen) && System.currentTimeMillis()<deadline){
            while(in.available()>0){
                int c=in.read();
                if(c<0)throw new IOException("Conexão encerrada");
                if(c=='>')return reply.toString().toUpperCase(Locale.US);
                reply.append((char)c);
            }
            Thread.sleep(18);
        }
        return reply.toString().toUpperCase(Locale.US);
    }
    private int[] bytes(String reply,String pid,int count){
        // ELM may return spaced, unspaced, echoed commands and CAN headers.
        String[] lines=reply.replace('\r','\n').split("\\n");
        for(String line:lines){
            String clean=line.replaceAll("[^0-9A-F]","");
            int ix=clean.indexOf("41"+pid);
            if(ix>=0 && clean.length()>=ix+4+count*2){
                int[] b=new int[count];
                try{for(int i=0;i<count;i++)b[i]=Integer.parseInt(clean.substring(ix+4+i*2,ix+6+i*2),16);return b;}
                catch(NumberFormatException ignored){}
            }
        }
        return null;
    }
    private int read1(InputStream in,OutputStream out,String pid,int gen)throws Exception{
        int[] b=bytes(command(in,out,"01"+pid,gen),pid,1);return b==null?-1:b[0];
    }
    private int readRpm(InputStream in,OutputStream out,int gen)throws Exception{
        int[] b=bytes(command(in,out,"010C",gen),"0C",2);return b==null?-1:((b[0]*256+b[1])/4);
    }
    private String voltage(InputStream in,OutputStream out,int gen)throws Exception{
        String reply=command(in,out,"ATRV",gen);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("([0-9]{1,2}\\.[0-9]{1,2})\\s*V").matcher(reply);
        return m.find()?m.group(1)+" V":"—";
    }
    private void run(BluetoothDevice device,Listener listener,int gen){
        try{
            if(device==null){status(listener,"Selecione o adaptador ELM327",false);return;}
            BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter==null||!adapter.isEnabled()){status(listener,"Ative o Bluetooth Android",false);return;}
            status(listener,"Conectando a "+device.getName()+"…",false);
            adapter.cancelDiscovery();
            BluetoothSocket current=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            socket=current;
            current.connect();
            InputStream in=current.getInputStream();OutputStream out=current.getOutputStream();
            command(in,out,"ATZ",gen);Thread.sleep(300);
            command(in,out,"ATE0",gen);command(in,out,"ATL0",gen);
            command(in,out,"ATS0",gen);command(in,out,"ATH0",gen);
            command(in,out,"ATSP0",gen);command(in,out,"0100",gen);
            status(listener,"ELM327 conectado",true);
            while(live(gen)){
                int rpm=readRpm(in,out,gen);
                int kmh=read1(in,out,"0D",gen);
                int temp=read1(in,out,"05",gen);if(temp>=0)temp-=40;
                String volts=voltage(in,out,gen);
                if(live(gen))data(listener,rpm,kmh,temp,volts);
                Thread.sleep(1100);
            }
        }catch(SecurityException e){if(live(gen))status(listener,"Bluetooth: permissão não concedida",false);}
        catch(Exception e){if(live(gen))status(listener,"Falha ELM327: "+e.getClass().getSimpleName(),false);}
        finally{
            if(live(gen)){running=false;status(listener,"OBD2 desconectado",false);}
            BluetoothSocket old=socket;socket=null;
            if(old!=null)try{old.close();}catch(Exception ignored){}
        }
    }
}