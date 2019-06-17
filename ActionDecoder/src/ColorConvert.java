

import java.util.HashMap;
import java.util.Map;

public class ColorConvert {
	static String TAG="color_convert";

	static Map<Byte, Short> RGB2HSV(Map<Byte, Short> bgr){
		Map<Byte, Short> hsv = new HashMap<Byte, Short>();
		int H=0;
		int S=0;
		int V=0;
		int R=bgr.get((byte)0);
		int G=bgr.get((byte)1);
		int B=bgr.get((byte)2);
		int MAX= Math.max(R,Math.max(G, B));
		int MIN=Math.min(R,Math.min(G, B));
		int DIF=(MAX-MIN);

		//R最大
		if(R>B && R>G){
			if(DIF!=0){
			H = 60 * ((G - B) / (MAX - MIN));
			}
		}else if(G>B && G>R){
			if(DIF!=0){
			H = 60 * ((B - R) / (MAX - MIN)) +120;
			}else{
			H=120;
			}
		}else if(B>R && B>G){
			if(DIF!=0){
			H = 60 * ((R - G) / (MAX - MIN)) +240;
			}else{
			H=240;
			}
		}else{
			H=0;
		}
		if(MAX!=0){
		S = (MAX - MIN) / MAX;
		}
		V = MAX;
		hsv.put((byte)0, (short)H);
		hsv.put((byte)1, (short)S);
		hsv.put((byte)2, (short)V);
		return hsv;
	}
}
