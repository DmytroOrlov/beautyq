package leaderboard.model

sealed trait QueryFailure extends Product with Serializable {
  def message: String
}

object QueryFailure {
  case class QueryExecutionFailure(
    queryName: String,
    message: String,
    underlying: Throwable,
  ) extends QueryFailure

  case class OperationFailure(
    operationName: String,
    message: String,
  ) extends QueryFailure

  case class DomainFailure(message: String) extends QueryFailure

  def fromThrowable(queryName: String, cause: Throwable): QueryFailure =
    QueryExecutionFailure(
      queryName,
      Option(cause.getMessage).getOrElse(cause.getClass.getName),
      cause,
    )

  def operation(operationName: String, message: String): QueryFailure =
    OperationFailure(operationName, message)

  def domain(message: String): QueryFailure =
    DomainFailure(message)
}
