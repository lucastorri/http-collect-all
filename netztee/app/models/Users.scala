package models

object Users {

  implicit class UserExtension(u: User) {
    def isAdmin = u.username == "lucastorri" && u.fid == "521558419"
  }

}
