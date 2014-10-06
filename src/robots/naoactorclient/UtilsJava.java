package robots.naoactorclient;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.apache.http.conn.util.InetAddressUtils;

import android.util.Log;


// http://stackoverflow.com/questions/11015912/how-do-i-get-ip-address-in-ipv4-format
public class UtilsJava {
	public static String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
//					System.out.println("ip1--:" + inetAddress);
//					System.out.println("ip2--:" + inetAddress.getHostAddress());

					// for getting IPV4 format
					String ipv4 = null;
					if (!inetAddress.isLoopbackAddress()
							&& InetAddressUtils
									.isIPv4Address(ipv4 = inetAddress
											.getHostAddress())) {

//						String ip = inetAddress.getHostAddress().toString();
//						System.out.println("ip---::" + ip);
						return ipv4;
					}
				}
			}
		} catch (Exception ex) {
			Log.e("IP Address", ex.toString());
		}
		return null;
	}

	public static double between(double min, double x, double max) {
		return Math.max(min, Math.min(x, max));
	}
}
