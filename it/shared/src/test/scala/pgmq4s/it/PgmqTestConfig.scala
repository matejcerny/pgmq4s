package pgmq4s.it

case class PgmqTestConfig(
    host: String = "localhost",
    port: Int = 5433,
    database: String = "pgmq",
    user: String = "pgmq",
    password: String = "pgmq"
):
  def jdbcUrl: String = s"jdbc:postgresql://$host:$port/$database"

object PgmqTestConfig:
  val default: PgmqTestConfig = PgmqTestConfig()
