package controllers

import concurrent.Future
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import org.apache.commons.codec.binary.Base64._
import play.api.libs.ws.{Response, WS}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws.WS.WSRequestHolder

object HmacClient {

  val userRegistry = Server("http://10.11.12.14:10111/")
  val harExport = Server("http://10.11.12.14:10230/")
  val key = "a7e5cae631be97b94ece34e27d64d20b62039ec2b861133128d6d3a55dc76347"
  val algorithm = "HmacSHA1"

  private def authorization(nonce: String, fields: String*) : Future[String] = Future {
    val secret = new SecretKeySpec(key.getBytes, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secret)
    val message = (fields :+ nonce).mkString("#")
    new String(encodeBase64(mac.doFinal(message.getBytes)))
  }

  private def req(path: Path, action: WSRequestHolder => Future[Response], fields: String*) =
    for {
      nonce <- WS.url(path.server.nonceUrl)
        .get.map(_.body)
      auth <- authorization(nonce, fields: _*)
      res <- action(WS.url(path.url).withHeaders("X-Nonce" -> nonce, "Authorization" -> auth))
    } yield res

  def get(path: Path, fields: String*) : Future[Response] =
    req(path, _.get, fields: _*)

  def put(path: Path, fields: String*) : Future[Response] =
    req(path, _.put(""), fields: _*)

  def delete(path: Path, fields: String*) : Future[Response] =
    req(path, _.delete, fields: _*)

  case class Server(baseUrl: String) {
    def nonceUrl = new URI(s"${baseUrl}/nonce").normalize.toString
    def path(p: String) = Path(this, p)
  }

  case class Path(server: Server, path: String) {
    val url = new URI(s"${server.baseUrl}/${path}").normalize.toString
  }
}
