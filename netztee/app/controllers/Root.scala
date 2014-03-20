package controllers

import play.api._, mvc._

object Root extends Controller {

  val login = Forbidden(views.html.Root.index())

  def index(path: String) = Authenticated.orElse(login) { implicit req: Request[_] =>
    Ok(views.html.Root.welcome())
  }

}
