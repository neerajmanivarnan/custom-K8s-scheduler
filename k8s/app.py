from flask import Flask
import psycopg2

app = Flask(__name__)

def connect_db():
    try:
        conn = psycopg2.connect(
            dbname="mydatabase",
            user="myuser",
            password="mypassword",
            host="postgres.default.svc.cluster.local",
            port="5432"
        )
        return conn
    except Exception as e:
        return str(e)

@app.route('/')
def index():
    conn = connect_db()
    if isinstance(conn, str):
        return f"Database Connection Failed: {conn}"
    return "Connected to PostgreSQL!"

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
