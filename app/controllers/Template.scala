package controllers

import play.api.mvc.Controller
import play.api.templates.Html

object Template extends Controller {

  def template(name: String) = Authenticated { implicit req =>
    try Ok(render(name))
    catch {
      case e: Exception => NotFound
    }
  }

  object render {

    import reflect.runtime.universe

    def apply(name: String) : Html = {
      val mirror = universe.runtimeMirror(getClass.getClassLoader)

      val templateObjectSymbol = mirror.staticModule(s"views.html.Template.$name")
      val moduleMirror = mirror.reflectModule(templateObjectSymbol)
      val instanceMirror = mirror.reflect(moduleMirror.instance)

      val tpe = instanceMirror.symbol.typeSignature
      val renderMethodTerm = universe.newTermName("apply")
      val methodSymbol = tpe.member(renderMethodTerm).asMethod

      instanceMirror.reflectMethod(methodSymbol)().asInstanceOf[Html]
    }
  }

}
