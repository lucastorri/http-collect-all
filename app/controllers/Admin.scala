package controllers

import play.api.mvc.Controller
import models.Users._
import HmacClient._
import concurrent.Future
import play.api.libs.json._
import scala.concurrent.ExecutionContext.Implicits.global
import models.User

object Admin extends Controller {

  def activate(user: String) = Authenticated.filter(_.isAdmin) { implicit req =>
    Async {
      put(userRegistry.path(s"/registry/${user}"), user)
        .map { _.status }
        .map {
          case 200 =>
            req.user.copy(active = true).save()
            Ok
          case _ =>
            InternalServerError
        }
        .fallbackTo(Future(InternalServerError))
    }
  }

  def deactivate(user: String) = Authenticated.filter(_.isAdmin) { implicit req =>
    Async {
      delete(userRegistry.path(s"/registry/${user}"), user)
        .map { _.status }
        .map {
          case 200 =>
            req.user.copy(active = false).save()
            Ok
          case _ =>
            InternalServerError
        }
        .fallbackTo(Future(InternalServerError))
    }
  }

  def status(user: String) = Authenticated.filter(_.isAdmin) { implicit req =>
    Async {
      get(userRegistry.path(s"/registry/${user}"), user)
        .map { _.status }
        .map {
          case 200 =>
            req.user.copy(active = true).save()
            Ok
          case 404 =>
            req.user.copy(active = false).save()
            NotFound
          case _ =>
            InternalServerError
        }
        .fallbackTo(Future(InternalServerError))
    }
  }

  def self = Authenticated { implicit req =>
    val user = req.user
    Ok(Json.obj(
      "isAdmin" -> user.isAdmin,
      "active" -> req.user.active,
      "username" -> req.user.username))
  }

  def users = Authenticated.filter(_.isAdmin) { implicit req =>
    val users = User.findAll().map { u =>
      Json.obj("id" -> u.id, "username" -> u.username, "fid" -> u.fid, "active" -> u.active)
    }
    Ok(Json.obj("users" -> users))
  }

}
