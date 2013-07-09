package controllers

import play.api._, mvc._, models._

object Root extends Controller {

  val login = play.api.templates.Html(<p>hello, please <a href="/login">login</a></p>.toString)

  def index = Authenticated.orElse(Redirect(routes.Root.welcome)) { implicit request : Request[AnyContent] =>
    Ok(s"${User.findAll} ## ${session}")
  }

  def welcome = Authenticated.orElse(Forbidden(login)) { implicit req : Request[AnyContent] =>
    Redirect(routes.Root.index)
  }

}
