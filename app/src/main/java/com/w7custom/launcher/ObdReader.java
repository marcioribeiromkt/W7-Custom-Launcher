package com.w7custom.launcher;
import android.bluetooth.*;
import android.os.*;
import java.io.*;
import java.util.*;
public class ObdReader {
 public interface Listener {void status(String s);void values(String s);}
 private final Handler main=new Handler(Looper.getMainLooper());private volatile boolean running=false;private BluetoothSocket socket;
 private void status(Listener l,String s){main.post(()->l.status(s));}
 private void values(Listener l,String s){main.post(()->l.values(s));}
 public void stop(){running=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}}
 private String command(InputStream in,OutputStream out,String cmd)throws Exception{
  out.write((cmd+"\r").getBytes("US-ASCII"));out.flush();StringBuilder b=new StringBuilder();long until=System.currentTimeMillis()+2800;
  while(System.currentTimeMillis()<until){while(in.available()>0){int c=in.read();if(c=='>' )return b.toString().replace("\r"," ").replace("\n"," ").toUpperCase(Locale.US);b.append((char)c);}Thread.sleep(25);}
  return b.toString().toUpperCase(Locale.US);
 }
 private int[] pid(String response,String pid){String h=response.replaceAll("[^0-9A-F]","");int pos=h.indexOf("41"+pid);if(pos<0||h.length()<pos+8)return null;try{return new int[]{Integer.parseInt(h.substring(pos+4,pos+6),16),Integer.parseInt(h.substring(pos+6,pos+8),16)};}catch(Exception e){return null;}}
 public void start(Listener listener){stop();running=true;new Thread(()->{
  try{
   BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null){status(listener,"Bluetooth indisponível");return;}
   BluetoothDevice chosen=null;for(BluetoothDevice d:adapter.getBondedDevices()){String n=d.getName();if(n!=null&&(n.toUpperCase(Locale.US).contains("OBD")||n.toUpperCase(Locale.US).contains("ELM")||n.toUpperCase(Locale.US).contains("VLINK"))){chosen=d;break;}}
   if(chosen==null){status(listener,"Emparelhe o ELM327 nas configurações Bluetooth");return;}
   status(listener,"Conectando ao "+chosen.getName());adapter.cancelDiscovery();
   socket=chosen.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));socket.connect();
   InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
   command(in,out,"ATZ");command(in,out,"ATE0");command(in,out,"ATL0");command(in,out,"ATS0");command(in,out,"ATH0");command(in,out,"ATSP0");
   status(listener,"ELM327 conectado");
   while(running){
    int[] rpm=pid(command(in,out,"010C"),"0C");int[] speed=pid(command(in,out,"010D"),"0D");int[] temp=pid(command(in,out,"0105"),"05");
    String r=rpm==null?"—":String.valueOf((rpm[0]*256+rpm[1])/4);String v=speed==null?"—":String.valueOf(speed[0]);String t=temp==null?"—":String.valueOf(temp[0]-40);
    values(listener,"RPM  "+r+"     Velocidade  "+v+" km/h\nMotor  "+t+" °C     ELM327 conectado");Thread.sleep(1200);
   }
  }catch(SecurityException e){status(listener,"Permissão Bluetooth necessária");}
   catch(Exception e){if(running)status(listener,"Falha OBD2: "+e.getClass().getSimpleName());}
   finally{stop();}
 },"W7-OBD2").start();}
}