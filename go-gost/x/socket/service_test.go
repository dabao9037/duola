package socket

import "testing"

// 缺失的服务名不应让整批删除失败：面板习惯同时发 <name>_tcp 和 <name>_udp，
// 历史上只建了其中一个的转发会因此永远删不掉。
func TestDeleteServicesSkipsMissingServices(t *testing.T) {
	req := deleteServicesRequest{Services: []string{"not_exist_tcp", "not_exist_udp"}}
	if err := deleteServices(req); err != nil {
		t.Fatalf("缺失服务不应返回错误，实际: %v", err)
	}
}

func TestDeleteServicesRejectsEmptyList(t *testing.T) {
	if err := deleteServices(deleteServicesRequest{}); err == nil {
		t.Fatal("空服务列表应当报错")
	}
}
