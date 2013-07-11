package controllers

import play.api._, mvc._, controllers._
import play.api.mvc.Controller
import play.api.Routes
import play.core.Router.JavascriptReverseRoute

object Javascript extends Controller {

  def all = Authenticated { implicit request =>
    Ok(Routes.javascriptRouter("netztee")(selfRoutes:_*)).as("text/javascript")
  }

  val selfRoutes = {
    val jsRoutesClass = classOf[routes.javascript]
    val controllers = jsRoutesClass.getFields.map(_.get(null))
    controllers.flatMap { controller =>
      controller.getClass.getDeclaredMethods.map { action =>
        action.invoke(controller).asInstanceOf[JavascriptReverseRoute]
      }
    }
  }

}
