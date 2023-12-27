package mkt.common;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import common.fnd.FndMsg;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MKTS3PIC {

	/** 拍照需求表-拍照流程图 **/
	public final static String aos_flowpic = "https://clss.s3.amazonaws.com/"
			+ "%E6%8B%8D%E7%85%A7%E9%9C%80%E6%B1%82%E8%A1%A8-%E6%8B%8D%E7%85%A7%E6%B5%81%E7%A8%8B.jpg";
	/** 拍照需求表-视频流程图 **/
	public final static String aos_flowved = "https://clss.s3.amazonaws.com/"
			+ "%E6%8B%8D%E7%85%A7%E9%9C%80%E6%B1%82%E8%A1%A8-%E8%A7%86%E9%A2%91%E6%B5%81%E7%A8%8B.jpg";
	/** 设计需求表-图片链接前缀 **/
	public final static String aos_designpic2 = "file://192.168.70.60/产品图&视频/02 设计图片/4/";

	public static String GetItemPicture(String ItemNumber) {
		/*
		 * ①货号第一位字符是8，
		 * 
		 * 若第二位字符是3，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\83\SKU；
		 * 
		 * 若第二位字符是4，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\84\SKU；
		 * 
		 * 若第二位字符是其他，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\8X\SKU；
		 * 
		 * ②货号第一位字符是其他，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\货号首字符\SKU；
		 */
		String Start = ItemNumber.substring(0, 1);
		if (ItemNumber.startsWith("8")) {
			//ItemNumber = ItemNumber.substring(1, ItemNumber.length());
			if (ItemNumber.startsWith("83"))
				ItemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\83\\" + ItemNumber;
			else if (ItemNumber.startsWith("84"))
				ItemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\84\\" + ItemNumber;
			else
				ItemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\8X\\" + ItemNumber;
		} else
			ItemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\" + Start + "\\" + ItemNumber;
		return ItemNumber;
	}
	
	public static String decrypt (String value) {
		try {
			// 密钥
			byte[] keyBytes = FndMsg.getStatic("MMS_S3KEY").getBytes(StandardCharsets.UTF_8);
			// 偏移向量 TEST
			IvParameterSpec ivParameterSpec =
					new IvParameterSpec(FndMsg.getStatic("MMS_S3IV").getBytes(StandardCharsets.UTF_8));
			// 秘钥规格
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
			Cipher cipher = Cipher.getInstance(FndMsg.getStatic("MMS_S3CIPHER"));
			// 初始化
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParameterSpec);
			byte[] encData = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			// base64
			Base64.Encoder encoder = Base64.getEncoder();
			String encodedText = encoder.encodeToString(encData);
			FndMsg.debug(encodedText);
			return encodedText;
		} catch (Exception e) {
			e.printStackTrace();
			return value;
		}
	}
	
}
