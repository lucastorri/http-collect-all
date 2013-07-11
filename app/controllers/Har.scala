package controllers

import play.api.mvc.Controller
import org.apache.commons.codec.binary.Base64.encodeBase64
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import play.api.libs.ws.{Response, WS}
import concurrent.Future
import play.api.libs.json.Json
import play.api.libs.Jsonp
import scala.concurrent.ExecutionContext.Implicits.global

object Har extends Controller {

  val key = "368cbd806dbe4be9354485ed51b2bd6503cf79db42eea7efa5e8472eab831ae9"
  val algorithm = "HmacSHA1"

  def har(bucket: String) = Authenticated { implicit req =>
    Async {
      fetchHar(bucket).map(Ok(_))
    }
  }

  def harp(bucket: String, callback: String) = Authenticated { implicit req =>
    Async {
      fetchHar(bucket).map { har => Ok(Jsonp(callback, har)) }
    }
  }

  def buckets() = Authenticated { implicit req =>
    Async {
      fetchBuckets.map { buckets => Ok(buckets) }
    }
  }

  private def authorization(nonce: String, fields: String*) : Future[String] = Future {
    val secret = new SecretKeySpec(key.getBytes, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secret)
    val message = (fields :+ nonce).mkString("#")
    println("###MESSAGE", message)
    new String(encodeBase64(mac.doFinal(message.getBytes)))
  }

  private def get(path: String, fields: String*)(implicit req: AuthenticatedRequest[_]) =
    for {
      nonce <- WS.url("http://localhost:10230/nonce")
          .get.map(_.body)
      auth <- authorization(nonce, fields: _*)
      res <- WS.url(s"http://localhost:10230/${path}")
          .withHeaders("X-Nonce" -> nonce, "Authorization" -> auth).get
    } yield res

  private def fetchHar(bucket: String)(implicit req: AuthenticatedRequest[_]) =
    get(s"${req.user.username}/${bucket}", req.user.username, bucket).map(r => Json.parse(r.body))

  private def fetchBuckets()(implicit req: AuthenticatedRequest[_]) =
    get(s"${req.user.username}", req.user.username).map { r => println("###BODY", r.status, r.body); Json.parse(r.body) }

}
