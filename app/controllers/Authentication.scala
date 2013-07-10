package controllers

import play.api._, mvc._, models._

import play.api.libs.json._
import play.api.mvc.Results._
import play.api.libs.ws._
import scala.util.Random
import concurrent.Future
import play.api.libs.ws.{Response => WSResponse}
import play.api.mvc.BodyParsers._
import scala.concurrent.ExecutionContext.Implicits.global
import scalikejdbc.SQLInterpolation._


case class AuthenticatedRequest[A](user: User, request: Request[A]) extends WrappedRequest(request)

trait Authenticated {

  type Handler[A] = AuthenticatedRequest[A] => Result

  protected val _orElse : Result = Forbidden

  def user[A](implicit req: Request[A]) =
    req.session.get("id").flatMap(id => User.find(id.toLong))

  def apply[A](p: BodyParser[A])(h: Handler[A]) : Action[A] = Action(p) { implicit req : Request[A] =>
    user
      .map(u => h(AuthenticatedRequest(u, req)))
      .getOrElse(_orElse)
  }

  def apply(h: Handler[AnyContent]) : Action[AnyContent] =
    apply(parse.anyContent)(h)
}
object Authenticated extends Authenticated {

  def orElse(r: Result) = new Authenticated {
    override protected val _orElse = r
  }

}

trait Authentication extends Controller {

  val clientId = "423110074427115"
  val clientSecret = "9c71342a00df5d674c65aa9a470dc6ac"
  
  val redirectUrl = s"http://localhost:9000${routes.Authentication.auth}" //req host, port and scheme

  def loginUrl(nonce: String) = s"http://www.facebook.com/dialog/oauth/?client_id=${clientId}&redirect_uri=${redirectUrl}&state=${nonce}"
  def tokenUrl(code: String) = s"https://graph.facebook.com/oauth/access_token?redirect_uri=${redirectUrl}&client_secret=${clientSecret}&client_id=${clientId}&code=${code}"
  def uInfoUrl(token: String) = s"https://graph.facebook.com/me?access_token=${token}"

  val TokenFormat = """access_token=([a-zA-Z\d\-_]+)&expires=\d+""".r

  def login = Action { implicit request =>
    val nonce = createNonce
    Redirect(loginUrl(nonce)).withSession(session + ("fb-nonce" -> nonce))
  }

  def auth = Action { implicit request =>

    val user = for {
      original <- future(session.get("fb-nonce"))
      received <- future(request.queryString("state").headOption)
      if original == received
      code <- future(request.queryString("code").headOption)
      TokenFormat(token) <- userToken(code)
      userInfo <- userInfo(token)
      fid <- future((userInfo \ "id").asOpt[String])
      username <- future((userInfo \ "username").asOpt[String])
    } yield {
      val json = extract(userInfo)
      val user = User.findAllBy(sqls.eq(User.column.fid, fid))
        .headOption
        .getOrElse {
          User.create(
            fid = fid,
            username = username,
            firstName = json("first_name"),
            middleName = json("middle_name"),
            lastName = json("last_name"))
        }
      (token, user)
    }

    val newSession = user
      .map { case (token, user) =>
        session + ("fb-token" -> token) + ("id" -> user.id.toString) + ("user" -> user.username)
      }

    Async {
      newSession.fallbackTo(Future(session)).map { session =>
        Redirect(routes.Root.index).withSession(session - ("fb-nonce"))
      }
    }
  }

  def logout = Action { implicit request =>
    val newSession = session - ("fb-nonce") - ("fb-token") - ("user") - ("id")
    Redirect(routes.Root.index).withSession(newSession)
  }

  private def createNonce(): String =
    Random.alphanumeric.take(16).mkString

  private def userToken(code: String) : Future[String] =
    get(tokenUrl(code)).map(r => r.body)

  private def userInfo(token: String) : Future[JsValue] =
    get(uInfoUrl(token)).map(r => Json.parse(r.body))

  private def extract(jsValue: JsValue): (String) => Option[String] =
    field => (jsValue \ field).asOpt[String]

  private def future[A](o: Option[A]) : Future[A] =
    Future(o.get)

  private def get(url: String): Future[WSResponse] =
    WS.url(url).get

}

object Authentication extends Authentication
