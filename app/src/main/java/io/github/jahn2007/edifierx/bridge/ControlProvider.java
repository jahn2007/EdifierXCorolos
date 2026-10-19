package io.github.jahn2007.edifierx.bridge;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import io.github.jahn2007.edifierx.device.DeviceRuntime;

/** Typed primitives over Binder-backed ContentProvider.call; no broadcasts or arbitrary byte API. */
public final class ControlProvider extends ContentProvider {
    public static final Uri URI=Uri.parse("content://io.github.jahn2007.edifierx.control/state");
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        Context context=requireContext();CallerPolicy.enforce(context,Binder.getCallingUid());
        DeviceRuntime runtime=DeviceRuntime.get(context);String address=arg==null?"":arg;
        if("snapshot".equals(method))return runtime.snapshot(address);
        // Only the module UI can initiate a connection or enroll an address.
        boolean own=Binder.getCallingUid()==android.os.Process.myUid();
        if("connect".equals(method)&&own){runtime.connect(address,extras.getString("profile",""));return runtime.snapshot(address);}
        if("remember".equals(method)&&own){runtime.rememberVerified();return runtime.snapshot(address);}
        if(extras==null)throw new IllegalArgumentException("generation required");
        if(!context.getSharedPreferences("local",0).getBoolean("enabled",false)||!runtime.running())throw new IllegalStateException("control disabled");
        long generation=extras.getLong("generation",-1);var s=runtime.controller.state();
        if(!address.equals(s.address())||generation!=s.generation())throw new IllegalStateException("stale device");
        if("refresh".equals(method))runtime.controller.refreshState();
        else if("noise".equals(method))runtime.controller.setNoiseMode(address,generation,extras.getString("mode",""));
        else if("game".equals(method))runtime.controller.setGameMode(address,generation,extras.getBoolean("enabled"));
        else throw new IllegalArgumentException("unknown method");
        return runtime.snapshot(address);
    }
    private Context requireContext(){Context c=getContext();if(c==null)throw new IllegalStateException("provider not attached");return c;}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String sort){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return "application/vnd.edifierx.state";}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
