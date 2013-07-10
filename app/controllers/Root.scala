package controllers

import play.api._, mvc._, models._

object Root extends Controller {

  val login = play.api.templates.Html(<p>hello, please <a href="/login">login</a></p>.toString)

  def index = Authenticated.orElse(Forbidden(views.html.Root.index.render)) { implicit request : Request[AnyContent] =>
    Ok(s"${User.findAll} ## ${session}")
  }

}
