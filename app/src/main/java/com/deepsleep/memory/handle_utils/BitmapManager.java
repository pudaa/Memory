package com.deepsleep.memory.handle_utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

public class BitmapManager {
	
	private static BitmapManager instance;		
	
    private BitmapManager() {  
    	Log.i("test", "---------------BitmapManager-----");  
    }
	
    public synchronized static BitmapManager getInstance() { 
        if (null == instance) { 
            instance = new BitmapManager();
        } 
        return instance; 
    }      
    
	public static Bitmap decodebitmap(byte[] data) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = false;  
		BitmapFactory.decodeByteArray(data, 0, data.length, options);
		

		options.inJustDecodeBounds = false;
		Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);		
		
		return bitmap;
	}
	public static Bitmap decodebitmapScale(byte[] data) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		//
		options.inJustDecodeBounds = false;
		BitmapFactory.decodeByteArray(data, 0, data.length, options);

		int realwidth = options.outWidth;
		int realheight = options.outHeight;
		//
		int scaleX = realwidth  / 100;
		int scaleY = realheight / 100;
		if (scaleX <= scaleY) {
			options.inSampleSize = scaleX;
		} else {
			options.inSampleSize = scaleY;
		}

		options.inJustDecodeBounds = false;
		Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

		return bitmap;
	}
	public static byte[] readStream(InputStream inStream) {
		ByteArrayOutputStream outStream = null;
		try {
			outStream = new ByteArrayOutputStream();        
	        byte[] buffer = new byte[1024];        
	        int len = 0;        
	        while( (len = inStream.read(buffer)) != -1){        
	            outStream.write(buffer, 0, len);        
	        }        
	        
	        return outStream.toByteArray();
		} catch (Exception ex) {
			
		} finally {
			try {
				if (null != inStream) {
					inStream.close(); 
				}	
				if (null != outStream) {
					outStream.close(); 
				}
			} catch (IOException e) {
				e.printStackTrace();
			}					 			        
		}
           
        return null;        
    } 
	
	public static Bitmap scaleByMatrix(Bitmap bitmap, int width, int height) {
		int oldWidth  = bitmap.getWidth();
	    int oldHeight = bitmap.getHeight();
	    	    
	    float scaleWidth = ((float) width) / oldWidth;
        float scaleHeight = ((float) height) / oldHeight;
        
        float scale;
        if (scaleWidth >= scaleHeight) {
        	scale = scaleWidth;
        } else {
        	scale = scaleHeight;
        }
        
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
        		oldWidth, oldHeight, matrix, true);
        
		return resizedBitmap;
	}
}
