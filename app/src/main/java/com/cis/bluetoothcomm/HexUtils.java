package com.cis.bluetoothcomm;

public class HexUtils {
	private static final char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	public static String hexToString(byte[] ba, int offset, int length) {
		char[] buf = new char[length * 2];
		int j = 0;
		int k;

		String str = "";
		for (int i = offset; i < offset + length; i++) {
			k = ba[i];
			buf[j++] = hexDigits[(k >>> 4) & 0x0F];
			str = str + buf[j - 1];
			buf[j++] = hexDigits[k & 0x0F];
//			str = str + buf[j - 1] + " ";
			str = str + buf[j - 1];
		}
		return str;
	}

	public static String hexToString(byte[] ba)
	{
		return hexToString(ba, 0, ba.length);
	}
	
	public static byte[] byteToHex(byte[] ba, int offset, int length) {
		byte[] buf = new byte[length * 2];
		int j = 0;
		int k;

		for (int i = offset; i < offset + length; i++) {
			k = ba[i];
			buf[j++] = (byte) hexDigits[(k >>> 4) & 0x0F];
			buf[j++] = (byte) hexDigits[k & 0x0F];
		}
		return buf;
	}
		
	public static byte[] hexStringToByteArray( String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		
		for( int i = 0; i < len; i += 2 ) {
			data[i/2] = (byte)((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
		}
		
		return data;
	}
	
	public static String byteToHexStringPrint( byte[] ba, int offset, int length ) {
		StringBuilder sb = new StringBuilder();
		
		for( int i = offset; i < offset + length; i++ ) {
			byte b = ba[i];
			sb.append( String.format("[%03d] 0X%02x ", i, (b & 0xFF)) );
		}
		
		return sb.toString();
	}
	
	public static String byteToHexStringPrint( byte[] ba ) {
		return byteToHexStringPrint( ba, 0, ba.length );
	}
};
