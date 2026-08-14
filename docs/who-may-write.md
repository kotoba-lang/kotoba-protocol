# who may write

host 名では書けない。鍵で書く。

- 主体: `did:key`
- 名: 鍵由来 IPNS
- 委任: CACAO。depth-1 自己 mint が自 graph の既定
- 検閲: Governor。拒否した書込を actor は実行しない

CreateLink 相当の overlay は、ここに通してから datom log / DHT metadata へ。
block を書き換える許可ではないので、対象 Entry の CID は動かない。

inga は「自分の chain 以外に書けない」を採る。conductor は採らない。
無順序の書き込み面も採らない — Datalog join が ref 1 本だから。
