package controllers

import play.api.mvc.Controller
import play.api.libs.json.Json
import play.api.libs.Jsonp
import scala.concurrent.ExecutionContext.Implicits.global
import HmacClient._

object Har extends Controller {

  def har(bucket: String) = Authenticated { implicit req =>
    Async {
      harFor(bucket).map(Ok(_))
    }
  }

  def harp(bucket: String, callback: String) = Authenticated { implicit req =>
    Async {
      harFor(bucket).map { har => Ok(Jsonp(callback, har)) }
    }
  }

  def buckets() = Authenticated { implicit req =>
    Async {
      userBuckets.map { buckets => Ok(buckets) }
    }
  }

  private def harFor(bucket: String)(implicit req: AuthenticatedRequest[_]) =
    get(harExport.path(s"${req.user.username}/${bucket}"), req.user.username, bucket).map(r => Json.parse(r.body))

  private def userBuckets()(implicit req: AuthenticatedRequest[_]) =
    get(harExport.path(s"${req.user.username}"), req.user.username).map(r => Json.parse(r.body))

}
