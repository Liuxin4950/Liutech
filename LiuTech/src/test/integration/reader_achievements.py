"""本地真实 API + MySQL 回归。临时账号/文章/触发器只服务本次测试，finally 按已创建 ID 清理。

前置：主后端已启动且使用同一本地 liutech 库；已运行成就迁移。
依赖：mysql-connector-python、bcrypt；通过环境变量 DB_PASSWORD 传入数据库密码。
运行：python LiuTech/src/test/integration/reader_achievements.py
"""
import concurrent.futures
import json
import os
import secrets
import urllib.error
import urllib.request
import uuid
import bcrypt
import mysql.connector

BASE = 'http://127.0.0.1:8080'
marker = 'codex_verify_' + uuid.uuid4().hex[:12]
trigger = marker + '_ledger_failure'
db = mysql.connector.connect(host='127.0.0.1', port=3306, user='root',
    password=os.environ['DB_PASSWORD'], database='liutech', use_pure=True, autocommit=True)
cursor = db.cursor()
users = []
posts = []


def api(path, token=None, method='GET', body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(BASE + path, headers=headers, method=method,
        data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def ok(path, token, method='GET', body=None):
    status, response = api(path, token, method, body)
    assert status == 200 and response.get('code') == 200, (path, status, response.get('message'))
    return response.get('data')


def account(suffix):
    password = secrets.token_urlsafe(24)
    name = marker + suffix
    cursor.execute('INSERT INTO users(username,email,password_hash,role,status,points,version) VALUES(%s,%s,%s,%s,1,0,0)',
        (name, name + '@example.invalid', bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode(), 'user'))
    users.append(cursor.lastrowid)
    result = ok('/user/login', None, 'POST', {'username': name, 'password': password})
    return users[-1], result['token']


try:
    uid, token = account('_a')
    _, other = account('_b')
    assert api('/user/achievements')[0] == 401
    assert api('/user/activities')[0] == 401
    assert api('/user/activities?page=0', token)[0] >= 400
    assert api('/user/achievements/unknown/claim', token, 'POST')[0] >= 400
    cursor.execute('SELECT id FROM categories WHERE deleted_at IS NULL LIMIT 1')
    category = cursor.fetchone()[0]
    for i in range(11):
        cursor.execute('INSERT INTO posts(title,content,category_id,author_id,status) VALUES(%s,%s,%s,%s,%s)',
            (marker + str(i), '<p>Temporary integration fixture</p>', category, uid, 'published' if i < 10 else 'draft'))
        posts.append(cursor.lastrowid)
    assert api(f'/posts/{posts[-1]}/view', token, 'POST')[0] >= 400
    for post in posts[:9]:
        ok(f'/posts/{post}/view', token, 'POST')
    for _ in range(3):
        ok(f'/posts/{posts[0]}/view', token, 'POST')
    tasks = {task['code']: task for task in ok('/user/achievements', token)}
    assert tasks['read_10']['progress'] == 9
    assert api('/user/achievements/read_10/claim', token, 'POST')[0] >= 400
    ok(f'/posts/{posts[9]}/view', token, 'POST')
    for i in range(10):
        cursor.execute('INSERT INTO comments(post_id,user_id,content) VALUES(%s,%s,%s)', (posts[0], uid, marker + str(i)))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        responses = list(executor.map(lambda _: ok('/user/achievements/comment_10/claim', token, 'POST'), range(8)))
    assert all(result['achievement']['status'] == 'claimed' for result in responses)
    cursor.execute('SELECT points FROM users WHERE id=%s', (uid,))
    assert cursor.fetchone()[0] == 2
    cursor.execute('SELECT COUNT(*) FROM points_transactions WHERE user_id=%s', (uid,))
    assert cursor.fetchone()[0] == 1
    # 只让此测试账号的流水写入失败，实际请求必须回滚先前的余额和领取记录。
    cursor.execute(f"CREATE TRIGGER `{trigger}` BEFORE INSERT ON points_transactions FOR EACH ROW BEGIN IF NEW.user_id={int(uid)} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='integration ledger failure'; END IF; END")
    assert api('/user/achievements/read_10/claim', token, 'POST')[0] >= 400
    cursor.execute('SELECT points FROM users WHERE id=%s', (uid,))
    assert cursor.fetchone()[0] == 2
    cursor.execute("SELECT COUNT(*) FROM user_achievement_claims WHERE user_id=%s AND achievement_code='read_10'", (uid,))
    assert cursor.fetchone()[0] == 0
    cursor.execute(f'DROP TRIGGER `{trigger}`')
    assert ok('/user/achievements/read_10/claim', token, 'POST')['points'] == 4
    stats = ok('/user/stats', token)
    assert stats['viewCount'] == 10 and stats['commentCount'] == 10 and stats['points'] == 4
    first = ok('/user/activities', token)
    second = ok('/user/activities?page=2', token)
    assert not ({item['id'] for item in first['records']} & {item['id'] for item in second['records']})
    times = [item['occurredAt'] for item in first['records'] + second['records']]
    assert times == sorted(times, reverse=True)
    assert all(item['type'] == 'register' for item in ok('/user/activities', other)['records'])
    ok('/posts/view-history', token, 'DELETE')
    assert ok('/user/stats', token)['viewCount'] == 0
    assert ok('/user/achievements/read_10/claim', token, 'POST')['points'] == 4
    assert all(item['type'] != 'view' for item in ok('/user/activities', token)['records'])
    print('PASS: auth, draft rejection, distinct views, threshold, 8 concurrent claims, ledger rollback, retry, statistics, activity order/paging/isolation, clear-history retention')
finally:
    cursor.execute(f'DROP TRIGGER IF EXISTS `{trigger}`')
    for uid in users:
        cursor.execute('SELECT username FROM users WHERE id=%s', (uid,))
        row = cursor.fetchone()
        if not row or not row[0].startswith(marker):
            raise RuntimeError('Fixture ownership check failed')
        for table in ['user_achievement_claims', 'points_transactions', 'comments', 'user_view_history']:
            cursor.execute(f'DELETE FROM {table} WHERE user_id=%s', (uid,))
    for post in posts:
        cursor.execute('DELETE FROM posts WHERE id=%s AND title LIKE %s', (post, marker + '%'))
    for uid in users:
        cursor.execute('DELETE FROM users WHERE id=%s AND username LIKE %s', (uid, marker + '%'))
    cursor.close()
    db.close()
    print('Temporary fixtures and failure trigger removed')
