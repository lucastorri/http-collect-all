package models

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class UserSpec extends Specification {
  val u = User.syntax("u")

  "User" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = User.find(1L)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = User.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = User.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = User.findAllBy(sqls.eq(u.id, 1L))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = User.countBy(sqls.eq(u.id, 1L))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = User.create(fid = "MyString", username = "MyString", active = false)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = User.findAll().head
      val updated = User.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = User.findAll().head
      User.destroy(entity)
      val shouldBeNone = User.find(1L)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        