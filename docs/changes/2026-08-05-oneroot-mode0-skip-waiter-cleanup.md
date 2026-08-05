# OneRoot mode0ï¼šè·³è¿‡ waiter cleanup é—¨é—©

## ç°è±¡

`comet` / `CP2A.260705.006` ä¸Šï¼š

- `slide-kaslr-ok` â†’ `prepare_kernel_page mode=0` â†’ CFI/pipe physrw æˆåŠŸåˆ°  
  `phys step tasklist compare ok=1`
- éšå `configfs * len=40` åˆ·å±ï¼ˆ`user_pipe_buffer`ï¼‰ï¼Œæ—  `root cred patched`
- å¯è‡´ `kernel_panic,oops:_fatal_exception`

App å†… so SHA æ›¾ä¸ READMEã€Œå·²éªŒè¯ã€å“ˆå¸Œ `e74cbc7dâ€¦` ä¸€è‡´â€”â€”ä¸æ˜¯è£…é”™åŒ…ã€‚

## æ ¹å› 

`install_child_root`ï¼ˆtokay è¦†ç›–ï¼‰ï¼š

```c
return install_pipe_physrw(fd) && cleanup_main_waiter_pi_state(fd) &&
       install_android_root(fd);
```

cleanup å¤±è´¥æˆ–å¡åœ¨ `find_task_by_tgid` çš„ pipe_phys æ—¶ï¼Œæ°¸è¿œè¿›ä¸äº† `install_android_root`ã€‚

åæ±‡ç¼–ï¼ˆåŸ soï¼‰ï¼šVA `0x28c5c` `bl cleanup`ï¼›`0x28c60` `cbz w0, return0`ã€‚

## ä¿®å¤

| å±‚ | å†…å®¹ |
|---|---|
| æºç  | `OneSo-factory/.../tokay-CP2A.260605.012/fops.c`ï¼šcleanup best-effort + `SKIP_WAITER_CLEANUP` |
| é¢„ç¼–è¯‘ so | NOP ä¸Šè¿° `bl`+`cbz`ï¼›æ–° SHA256 `3c3c0868â€¦`ï¼›åŸä»¶å¤‡ä»½ `*.orig-e74cbc7d` |
| App | åŒæ­¥ assets soï¼Œç‰ˆæœ¬ **1.1.5 / 16** |

## éªŒè¯

- é™æ€ï¼šè¡¥ä¸ä½ç‚¹å­—èŠ‚ä¸º `1f2003d5`ï¼ˆNOPï¼‰Ã—2ï¼›å“ˆå¸Œè§ä¸Šã€‚
- åŠ¨æ€ï¼šéœ€çœŸæœºå†è·‘ï¼ˆä»æœ‰ panic é£é™©ï¼‰ï¼›æœŸæœ›æ—¥å¿—å‡ºç° `root cred patched` / `pipe physrw` æˆåŠŸæ”¶å°¾ï¼Œæˆ–è‡³å°‘è¶Šè¿‡ cleanup åˆ·å±ã€‚
# ²¹³ä£º1.1.5 ÈÔÊ§°Ü + reboot,shell + 1.1.6 walk ÉÏÏŞ

- 1.1.5 ¼à¿´£ºÈÈĞŞ so ÒÑÅÜµ½£¬cleanup Ìø¹ı£»ÈÔ¿¨ `tasklist` ºó `find_task` pipe_phys£¨6030¡Áconfigfs£©¡£
- ËæºóÏµÍ³Òì³££º`package` ·şÎñÒ»¶È²»¿ÉÓÃ£¬`exploit_service` ²ĞÁô£»ÖØÁ¬ºó `sys.boot.reason.last=reboot,shell`£¨±¾ÂÖ²»ÊÇ kernel_panic£©¡£
- 1.1.6£ºÔÙÈÈĞŞ `find_task` Ñ­»·ÉÏÏŞ `mov w8,#0x1001` ¡ú `#0x41`£¨Ô¼ 64 ´Î£©£¬so SHA `64ed9d74¡­`£»ÒÑ×°»ú£¬**ÏÈ±ğµã**£¬µÈÊÚÈ¨ÔÙ¼à¿´¡£
