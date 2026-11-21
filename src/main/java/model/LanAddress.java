package model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class LanAddress {
    private static final Pattern IPV4 = Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private LanAddress() {
    }

    public static String normalize(String input) {
        String address = input.strip();
        if (IPV4.matcher(address).matches()) {
            for (String octet : address.split("\\.")) {
                if ((octet.length() > 1 && octet.startsWith("0")) || Integer.parseInt(octet) > 255) {
                    throw invalid();
                }
            }
            return address;
        }
        if (address.indexOf(':') >= 0 && IPV6.matcher(address).matches()) {
            try {
                return InetAddress.getByName("[" + address + "]").getHostAddress();
            } catch (UnknownHostException failure) {
                throw invalid();
            }
        }
        throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Nhập địa chỉ IPv4 hoặc IPv6 hợp lệ.");
    }
}
