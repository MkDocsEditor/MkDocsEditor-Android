// Generates and checks Automerge interop fixtures between automerge-go (backend)
// and automerge-java (Android).
package main

import (
	"bufio"
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"

	"github.com/automerge/automerge-go"
)

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func content(d *automerge.Doc) string {
	v, err := d.Path("content").Get()
	must(err)
	s, err := v.Text().Get()
	must(err)
	return s
}

// record runs a Go<->Go sync session and returns the messages from server to peer.
func record(server, peer *automerge.Doc) [][]byte {
	ss := automerge.NewSyncState(server)
	ps := automerge.NewSyncState(peer)
	var out [][]byte
	for i := 0; i < 100; i++ {
		m1, ok1 := ss.GenerateMessage()
		if ok1 {
			out = append(out, m1.Bytes())
			_, err := ps.ReceiveMessage(m1.Bytes())
			must(err)
		}
		m2, ok2 := ps.GenerateMessage()
		if ok2 {
			_, err := ss.ReceiveMessage(m2.Bytes())
			must(err)
		}
		if !ok1 && !ok2 {
			return out
		}
	}
	panic("no convergence")
}

func gen(dir string) {
	doc := automerge.New()
	must(doc.SetActorID("00000000000000000000000000000002"))
	must(doc.RootMap().Set("content", automerge.NewText("Hällo Wörld 😀\n")))
	_, err := doc.Commit("import")
	must(err)
	base := doc.Save()
	must(os.WriteFile(filepath.Join(dir, "go-base.automerge"), base, 0o644))

	// server edit after the emoji (code point index 13)
	must(doc.SetActorID("00000000000000000000000000000001"))
	t, err := doc.Path("content").Get()
	must(err)
	must(t.Text().Insert(13, " from Go"))
	_, err = doc.Commit("disk edit")
	must(err)
	must(os.WriteFile(filepath.Join(dir, "go-edited.automerge"), doc.Save(), 0o644))
	must(os.WriteFile(filepath.Join(dir, "go-edited.txt"), []byte(content(doc)), 0o644))

	peer, err := automerge.Load(base)
	must(err)
	for i, m := range record(doc, peer) {
		must(os.WriteFile(filepath.Join(dir, fmt.Sprintf("go-sync-%d.bin", i)), m, 0o644))
	}
	if content(peer) != content(doc) {
		panic("go peer did not converge")
	}
}

// check loads a document saved by automerge-java.
func check(dir string) {
	raw, err := os.ReadFile(filepath.Join(dir, "java-edited.automerge"))
	must(err)
	jd, err := automerge.Load(raw)
	must(err)
	fmt.Printf("java save loaded by go: %q\n", content(jd))
}

// serve runs a live sync session over stdin/stdout, holding go-edited.automerge.
// Each line is "MSG <base64>" or "NONE". The server writes first. When both sides
// sent NONE in one round, it writes "TEXT <base64 of the text>" and exits.
func serve(dir string) {
	raw, err := os.ReadFile(filepath.Join(dir, "go-edited.automerge"))
	must(err)
	doc, err := automerge.Load(raw)
	must(err)
	st := automerge.NewSyncState(doc)
	in := bufio.NewScanner(os.Stdin)
	in.Buffer(make([]byte, 1<<20), 1<<24)
	for i := 0; i < 100; i++ {
		m, ok := st.GenerateMessage()
		if ok {
			fmt.Println("MSG " + base64.StdEncoding.EncodeToString(m.Bytes()))
		} else {
			fmt.Println("NONE")
		}
		if !in.Scan() {
			panic("stdin closed")
		}
		line := in.Text()
		if line == "NONE" {
			if !ok {
				fmt.Println("TEXT " + base64.StdEncoding.EncodeToString([]byte(content(doc))))
				return
			}
			continue
		}
		b, err := base64.StdEncoding.DecodeString(line[4:])
		must(err)
		_, err = st.ReceiveMessage(b)
		must(err)
	}
	panic("no convergence")
}

func main() {
	switch os.Args[1] {
	case "gen":
		gen(os.Args[2])
	case "check":
		check(os.Args[2])
	case "serve":
		serve(os.Args[2])
	}
}
