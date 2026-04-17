import java.net.http.*;
import java.net.*;
import java.time.*;
public class TestTls {
  public static void main(String[] args) throws Exception {
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    var req = HttpRequest.newBuilder()
      .uri(URI.create("https://api.siliconflow.cn/v1/models"))
      .timeout(Duration.ofSeconds(20))
      .header("Authorization", "Bearer sk-phkhkitzdhaleqeppfzntxfwoheuazazonqsppzhghlppeda")
      .GET().build();
    var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    System.out.println(resp.statusCode());
    System.out.println(resp.body().substring(0, Math.min(200, resp.body().length())));
  }
}
