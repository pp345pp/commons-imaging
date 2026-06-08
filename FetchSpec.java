import java.io.*;
import java.net.*;

public class FetchSpec {
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://developers.google.com/speed/webp/docs/webp_lossless_bitstream_specification");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            if (inputLine.contains("Simple Huffman code") || inputLine.contains("Code length")) {
                System.out.println(inputLine);
            }
        }
        in.close();
    }
}