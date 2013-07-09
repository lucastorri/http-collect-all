package models

import scalikejdbc._
import scalikejdbc.SQLInterpolation._

case class User(
  id: Long, 
  username: String) {

  def save()(implicit session: DBSession = User.autoSession): User = User.save(this)(session)

  def destroy()(implicit session: DBSession = User.autoSession): Unit = User.destroy(this)(session)

}
      

object User extends SQLSyntaxSupport[User] {

  override val tableName = "users"

  override val columns = Seq("id", "username")

  def apply(u: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = rs.long(u.id),
    username = rs.string(u.username)
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
    username: String)(implicit session: DBSession = autoSession): User = {
    val generatedKey = withSQL {
      insert.into(User).columns(
        column.username
      ).values(
        username
      )
    }.updateAndReturnGeneratedKey.apply()

    User(
      id = generatedKey, 
      username = username)
  }

  def save(m: User)(implicit session: DBSession = autoSession): User = {
    withSQL { 
      update(User as u).set(
        u.id -> m.id,
        u.username -> m.username
      ).where.eq(u.id, m.id)
    }.update.apply()
    m
  }
        
  def destroy(m: User)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(User).where.eq(column.id, m.id) }.update.apply()
  }
        
}
