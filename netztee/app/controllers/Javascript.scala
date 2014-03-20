package controllers

import play.api._, mvc._, controllers._
import play.api.mvc.Controller
import play.api.Routes
import play.core.Router.JavascriptReverseRoute

object Javascript extends Controller {

  def all = Authenticated { implicit req =>
    Ok(Routes.javascriptRouter("netztee", Some("netztee.ajax"))(selfRoutes:_*)).as("text/javascript")
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
