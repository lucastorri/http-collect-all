package models

import scalikejdbc._
import scalikejdbc.SQLInterpolation._

case class User(
  id: Long, 
  fid: String, 
  username: String, 
  firstName: Option[String] = None, 
  middleName: Option[String] = None, 
  lastName: Option[String] = None) {

  def save()(implicit session: DBSession = User.autoSession): User = User.save(this)(session)

  def destroy()(implicit session: DBSession = User.autoSession): Unit = User.destroy(this)(session)

}
      

object User extends SQLSyntaxSupport[User] {

  override val tableName = "users"

  override val columns = Seq("id", "fid", "username", "first_name", "middle_name", "last_name")

  def apply(u: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = rs.long(u.id),
    fid = rs.string(u.fid),
    username = rs.string(u.username),
    firstName = rs.stringOpt(u.firstName),
    middleName = rs.stringOpt(u.middleName),
    lastName = rs.stringOpt(u.lastName)
  )
      
  val u = User.syntax("u")

  val autoSession = AutoSession

  def find(id: Long)(implicit session: DBSession = autoSession): Option[User] = {
    withSQL { 
      select.from(User as u).where.eq(u.id, id)
    }.map(User(u.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[User] = {
    withSQL(select.from(User as u)).map(User(u.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(User as u)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[User] = {
    withSQL { 
      select.from(User as u).where.append(sqls"${where}")
    }.map(User(u.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(User as u).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    fid: String,
    username: String,
    firstName: Option[String] = None,
    middleName: Option[String] = None,
    lastName: Option[String] = None)(implicit session: DBSession = autoSession): User = {
    val generatedKey = withSQL {
      insert.into(User).columns(
        column.fid,
        column.username,
        column.firstName,
        column.middleName,
        column.lastName
      ).values(
        fid,
        username,
        firstName,
        middleName,
        lastName
      )
    }.updateAndReturnGeneratedKey.apply()

    User(
      id = generatedKey, 
      fid = fid,
      username = username,
      firstName = firstName,
      middleName = middleName,
      lastName = lastName)
  }

  def save(m: User)(implicit session: DBSession = autoSession): User = {
    withSQL { 
      update(User as u).set(
        u.id -> m.id,
        u.fid -> m.fid,
        u.username -> m.username,
        u.firstName -> m.firstName,
        u.middleName -> m.middleName,
        u.lastName -> m.lastName
      ).where.eq(u.id, m.id)
    }.update.apply()
    m
  }
        
  def destroy(m: User)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(User).where.eq(column.id, m.id) }.update.apply()
  }
        
}
