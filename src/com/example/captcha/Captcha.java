package com.example.captcha;

public class Captcha {

	static {
		try {
			System.loadLibrary("Captcha");
		} catch (UnsatisfiedLinkError ule) {
			System.err.println("WARNING: Could not load library libCaptcha.so!");
		}
	}
	
	public static native String getVCodeContent( byte[] data, int width, int height, int widthStep );
}
